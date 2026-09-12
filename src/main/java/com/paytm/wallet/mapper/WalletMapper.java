package com.paytm.wallet.mapper;

import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(target = "walletId", source = "id")
    WalletResponse toResponse(Wallet wallet);
}
