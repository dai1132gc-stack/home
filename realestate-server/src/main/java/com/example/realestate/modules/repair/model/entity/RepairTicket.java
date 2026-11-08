package com.example.realestate.modules.repair.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 报修工单。状态迁移统一由 RepairServiceImpl 的状态机 + 条件 UPDATE 控制。
 */
@Data
@NoArgsConstructor
@TableName("repair_ticket")
public class RepairTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private Long houseId;

    private Long contractId;

    private Long customerId;

    private String category;

    private String urgency;

    private String title;

    private String description;

    private String contactPhone;

    private Instant preferredVisitTime;

    private String status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
