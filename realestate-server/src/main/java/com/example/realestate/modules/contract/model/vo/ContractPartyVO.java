package com.example.realestate.modules.contract.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 合同签署方。phone 对 CUSTOMER 脱敏；idCardMasked 始终为脱敏值（前 6 后 4）。
 */
@Schema(description = "合同签署方")
public record ContractPartyVO(
        String partyType,
        String partyTypeLabel,
        String name,
        String phone,
        String idCardMasked
) {
}
