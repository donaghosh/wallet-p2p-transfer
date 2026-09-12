package com.paytm.wallet.mapper;

import com.paytm.wallet.dto.TransferResponse;
import com.paytm.wallet.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransferMapper {

    @Mapping(target = "transferId", source = "id")
    @Mapping(target = "from", source = "fromWalletId")
    @Mapping(target = "to", source = "toWalletId")
    TransferResponse toResponse(Transfer transfer);
}
