package com.paytm.wallet.service;

import com.paytm.wallet.config.DomainMetrics;
import com.paytm.wallet.constants.DeclineReason;
import com.paytm.wallet.constants.ErrorCode;
import com.paytm.wallet.constants.TransferStatus;
import com.paytm.wallet.dto.CreateTransferRequest;
import com.paytm.wallet.dto.TransferResponse;
import com.paytm.wallet.entity.Transfer;
import com.paytm.wallet.exception.BadRequestException;
import com.paytm.wallet.exception.ConflictException;
import com.paytm.wallet.exception.NotFoundException;
import com.paytm.wallet.mapper.TransferMapper;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money movement engine. The whole of {@link #create} is one transaction, so the
 * idempotency-key claim and the ledger debit/credit commit together or not at all.
 *
 * <p>Correctness mechanisms:
 * <ul>
 *   <li><b>Exactly-once:</b> claim the key with {@code INSERT ... ON CONFLICT DO NOTHING};
 *       a concurrent duplicate loses the claim and reads back the committed result, so it
 *       never moves money.</li>
 *   <li><b>Deadlock-free:</b> lock both wallets in ascending id order before touching them,
 *       so A&rarr;B and B&rarr;A cannot deadlock.</li>
 *   <li><b>No overdraft:</b> the debit is an atomic conditional {@code UPDATE ... WHERE
 *       balance >= amount}; rows-affected 0 means decline, never a partial apply.</li>
 *   <li><b>Conservation:</b> debit and credit are equal and in the same transaction.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final TransferRepository transferRepository;
    private final WalletRepository walletRepository;
    private final TransferMapper transferMapper;
    private final DomainMetrics metrics;

    @Override
    @Transactional
    public TransferResponse create(CreateTransferRequest request) {
        if (request.from().equals(request.to())) {
            throw new BadRequestException(
                    ErrorCode.SELF_TRANSFER, "from and to must be different wallets");
        }
        String requestHash = requestHash(request);

        // Fast path: a plain (non-concurrent) retry returns the original result without work.
        var existing = transferRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        // Lock both wallets in ascending id order BEFORE claiming. The claim INSERT takes
        // implicit FK key-share locks on the referenced wallet rows in unsorted (from, to)
        // order; by holding FOR UPDATE on both rows in ascending id order first, every
        // transaction acquires the wallet locks in the same global order — the deadlock-free
        // guarantee for concurrent A->B and B->A transfers.
        lockWalletsInAscendingOrder(request);

        // Claim the key in the same transaction as the money move.
        int claimed = transferRepository.insertClaim(
                request.idempotencyKey(), requestHash, request.from(), request.to(), request.amountPaise());
        Transfer transfer = transferRepository.findByIdempotencyKey(request.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer row missing after claim for key " + request.idempotencyKey()));
        if (claimed == 0) {
            // Lost the race (or a concurrent replay): the row is the committed winner.
            return replayOrConflict(transfer, requestHash);
        }

        log.info("transfer.created transfer_id={} from={} to={} amount_paise={} key={}",
                transfer.getId(), request.from(), request.to(), request.amountPaise(),
                request.idempotencyKey());
        applyLedger(request, transfer);
        return transferMapper.toResponse(transfer);
    }

    /**
     * Locks the two wallet rows FOR UPDATE in ascending id order and validates existence.
     * Runs before the claim INSERT so the INSERT's implicit FK key-share locks on the parent
     * wallet rows are already held in a deterministic order.
     */
    private void lockWalletsInAscendingOrder(CreateTransferRequest request) {
        long firstId = Math.min(request.from(), request.to());
        long secondId = Math.max(request.from(), request.to());
        boolean firstExists = !walletRepository.lockWallet(firstId).isEmpty();
        boolean secondExists = !walletRepository.lockWallet(secondId).isEmpty();
        boolean fromExists = (request.from() == firstId) ? firstExists : secondExists;
        boolean toExists = (request.to() == firstId) ? firstExists : secondExists;
        if (!fromExists) {
            throw new NotFoundException(
                    ErrorCode.WALLET_NOT_FOUND, "from wallet " + request.from() + " not found");
        }
        if (!toExists) {
            throw new NotFoundException(
                    ErrorCode.WALLET_NOT_FOUND, "to wallet " + request.to() + " not found");
        }
    }

    /**
     * Applies the debit/credit (both wallet rows already locked) and stamps the terminal
     * status on the claimed transfer row. Runs inside {@link #create}'s transaction.
     */
    private void applyLedger(CreateTransferRequest request, Transfer transfer) {
        int debited = walletRepository.debit(request.from(), request.amountPaise());
        if (debited == 0) {
            transfer.setStatus(TransferStatus.DECLINED);
            transfer.setDeclineReason(DeclineReason.INSUFFICIENT_FUNDS.name());
            metrics.transferDeclinedInsufficientFunds();
            log.info("transfer.declined_insufficient_funds transfer_id={} from={} amount_paise={}",
                    transfer.getId(), request.from(), request.amountPaise());
            return;
        }
        walletRepository.credit(request.to(), request.amountPaise());
        transfer.setStatus(TransferStatus.SUCCEEDED);
        metrics.transferSucceeded();
        log.info("transfer.debited_and_credited transfer_id={} from={} to={} amount_paise={}",
                transfer.getId(), request.from(), request.to(), request.amountPaise());
    }

    private TransferResponse replayOrConflict(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key already used with a different request body");
        }
        metrics.idempotentReplay();
        log.info("transfer.idempotent_replay transfer_id={} key={} status={}",
                existing.getId(), existing.getIdempotencyKey(), existing.getStatus());
        return transferMapper.toResponse(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getById(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.TRANSFER_NOT_FOUND, "Transfer " + transferId + " not found"));
        return transferMapper.toResponse(transfer);
    }

    private String requestHash(CreateTransferRequest request) {
        return HashUtil.sha256Hex(request.from() + "|" + request.to() + "|" + request.amountPaise());
    }
}
