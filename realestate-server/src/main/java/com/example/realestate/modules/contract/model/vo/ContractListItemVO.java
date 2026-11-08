package com.example.realestate.modules.contract.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 合同列表项。statusLabel 在 Service 补全；pdfReady 表示服务端已生成 PDF。
 */
@Data
@Schema(description = "合同列表项")
public class ContractListItemVO {

    private Long id;

    private String contractNo;

    private String title;

    private Long houseId;

    private String houseTitle;

    private Long customerId;

    private String customerName;

    private BigDecimal amount;

    private BigDecimal deposit;

    private String paymentCycle;

    private String paymentCycleLabel;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String statusLabel;

    private Boolean pdfReady;

    private Instant signedAt;

    private Instant createdAt;
}
