package com.example.realestate.modules.contract.model.vo;

import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 合同详情：协议正文与签章区不通过接口返回，PDF 走鉴权流式下载；
 * 房屋信息来自创建时快照，房源后续修改不影响历史合同展示。
 */
@Schema(description = "合同详情")
public record ContractDetailVO(
        Long id,
        String contractNo,
        String title,
        Long houseId,
        String houseTitle,
        String houseType,
        String community,
        String region,
        String address,
        BigDecimal area,
        Integer rooms,
        Integer bathrooms,
        String floor,
        Long customerId,
        String customerName,
        Long ownerId,
        BigDecimal amount,
        BigDecimal deposit,
        String amountInWords,
        String paymentCycle,
        String paymentCycleLabel,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String statusLabel,
        String templateVersion,
        boolean pdfAvailable,
        Instant signedAt,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        List<ContractPartyVO> parties,
        List<TransactionVO> transactions,
        List<ContractStatusLogVO> statusLogs
) {
}
