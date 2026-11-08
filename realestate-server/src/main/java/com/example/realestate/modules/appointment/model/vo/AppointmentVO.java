package com.example.realestate.modules.appointment.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 预约视图。
 * contactPhone 对非 ADMIN 脱敏（138******78）；客户信息仅 ADMIN 可见。
 */
@Data
@Schema(description = "预约带看")
public class AppointmentVO {

    private Long id;

    private Long houseId;

    private String houseTitle;

    private String houseCommunity;

    private String houseRegion;

    private Long customerId;

    private String customerName;

    private String contactPhone;

    private Instant preferredTime;

    private String remark;

    private String status;

    private String statusLabel;

    private Instant createdAt;

    private Instant updatedAt;
}
