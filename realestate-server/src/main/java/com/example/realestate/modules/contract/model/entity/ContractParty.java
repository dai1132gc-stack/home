package com.example.realestate.modules.contract.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 合同签署方快照：出租方/承租方/居间方，创建后不随房东或客户资料变化。
 */
@Data
@NoArgsConstructor
@TableName("contract_party")
public class ContractParty {

    public static final String TYPE_LESSOR = "LESSOR";
    public static final String TYPE_LESSEE = "LESSEE";
    public static final String TYPE_AGENT = "AGENT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private String partyType;

    private String name;

    private String phone;

    /** 仅保存脱敏结果（前 6 后 4），不落库完整证件号 */
    private String idCardMasked;

    private Instant createdAt;
}
