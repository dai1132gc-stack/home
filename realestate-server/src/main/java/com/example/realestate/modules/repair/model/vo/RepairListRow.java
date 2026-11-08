package com.example.realestate.modules.repair.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Mapper 查询行（可变 POJO），Service 补齐标签与脱敏后转 VO。 */
@Data
@NoArgsConstructor
public class RepairListRow {

    private Long id;

    private String ticketNo;

    private Long houseId;

    private String houseTitle;

    private Long contractId;

    private String contractNo;

    private Long customerId;

    private String customerName;

    private String category;

    private String urgency;

    private String title;

    private String status;

    private String contactPhone;

    private Instant preferredVisitTime;

    private Instant createdAt;

    private Instant updatedAt;
}
