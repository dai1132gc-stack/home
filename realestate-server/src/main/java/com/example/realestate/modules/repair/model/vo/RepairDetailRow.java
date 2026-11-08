package com.example.realestate.modules.repair.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Mapper 查询行：详情标量字段，Service 再补齐标签、脱敏、图片与日志。 */
@Data
@NoArgsConstructor
public class RepairDetailRow {

    private Long id;

    private String ticketNo;

    private Long houseId;

    private String houseTitle;

    private String houseAddress;

    private Long contractId;

    private String contractNo;

    private Long customerId;

    private String customerName;

    private String category;

    private String urgency;

    private String title;

    private String description;

    private String status;

    private String contactPhone;

    private Instant preferredVisitTime;

    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;
}
