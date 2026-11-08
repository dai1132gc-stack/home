package com.example.realestate.modules.appointment.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 预约带看。状态迁移统一走 AppointmentServiceImpl 的状态机与条件更新，禁止直接 setStatus 后 updateById。
 */
@Data
@NoArgsConstructor
@TableName("viewing_appointment")
public class ViewingAppointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private Long customerId;

    private Instant preferredTime;

    private String contactPhone;

    private String remark;

    private String status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
