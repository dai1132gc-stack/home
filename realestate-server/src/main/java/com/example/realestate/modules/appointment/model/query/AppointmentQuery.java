package com.example.realestate.modules.appointment.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * 预约列表查询：状态、房源、客户与时间范围全部下推 SQL。
 * customerId 仅 ADMIN 生效；CUSTOMER 由 Service 强制为本人，忽略客户端传值。
 */
@Schema(description = "预约列表查询")
public class AppointmentQuery extends PageQuery {

    private String status;

    private Long houseId;

    private Long customerId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant fromTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant toTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Instant getFromTime() {
        return fromTime;
    }

    public void setFromTime(Instant fromTime) {
        this.fromTime = fromTime;
    }

    public Instant getToTime() {
        return toTime;
    }

    public void setToTime(Instant toTime) {
        this.toTime = toTime;
    }
}
