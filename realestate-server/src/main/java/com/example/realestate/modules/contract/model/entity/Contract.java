package com.example.realestate.modules.contract.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 租赁合同。状态迁移由 ContractServiceImpl 状态机 + 条件 UPDATE 控制；
 * active_flag 为数据库生成列，配合唯一键保证同一房源最多一份 PENDING_SIGN/ACTIVE 合同。
 */
@Data
@NoArgsConstructor
@TableName("contract")
public class Contract {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String contractNo;

    private Long houseId;

    private Long customerId;

    private Long ownerId;

    private String title;

    private BigDecimal amount;

    private BigDecimal deposit;

    private String paymentCycle;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private String templateVersion;

    private String snapshotJson;

    private String pdfUrl;

    private Instant signedAt;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
