package com.example.realestate.modules.contract.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 合同业务快照（写入 contract.snapshot_json）。
 * 生成 PDF 与详情展示都读取快照，保证房源、双方资料后续修改不影响历史合同。
 */
@Schema(description = "合同业务快照")
public record ContractSnapshot(
        String templateVersion,
        Instant generatedAt,
        House house,
        List<Party> parties,
        Terms terms,
        String clauses
) {

    public record House(
            Long id,
            String title,
            String houseType,
            String community,
            String region,
            String address,
            BigDecimal area,
            Integer rooms,
            Integer bathrooms,
            String floor
    ) {
    }

    public record Party(
            String type,
            String name,
            String phone,
            String idCardMasked
    ) {
    }

    public record Terms(
            BigDecimal amount,
            BigDecimal deposit,
            String paymentCycle,
            LocalDate startDate,
            LocalDate endDate,
            String amountInWords
    ) {
    }
}
