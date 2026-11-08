package com.example.realestate.modules.repair.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 报修状态变更日志：每次迁移必须写一条，便于审计与时间线展示。 */
@Data
@NoArgsConstructor
@TableName("repair_status_log")
public class RepairStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String reason;

    private Instant createdAt;
}
