package com.paytm.wallet.service;

import com.paytm.wallet.dto.CreateTransferRequest;
import com.paytm.wallet.dto.TransferResponse;

public interface TransferService {

    /** Create (or idempotently replay) a transfer. */
    TransferResponse create(CreateTransferRequest request);

    /** Fetch a transfer by id, or throw {@code NotFoundException}. */
    TransferResponse getById(Long transferId);
}
