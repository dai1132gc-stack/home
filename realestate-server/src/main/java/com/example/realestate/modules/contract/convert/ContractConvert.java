package com.example.realestate.modules.contract.convert;

import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.common.enums.PaymentCycle;
import com.example.realestate.common.util.PhoneMasker;
import com.example.realestate.infrastructure.pdf.ChineseAmountConverter;
import com.example.realestate.modules.contract.model.ContractSnapshot;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.entity.ContractParty;
import com.example.realestate.modules.contract.model.entity.ContractStatusLog;
import com.example.realestate.modules.contract.model.vo.ContractDetailVO;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;
import com.example.realestate.modules.contract.model.vo.ContractPartyVO;
import com.example.realestate.modules.contract.model.vo.ContractStatusLogVO;
import com.example.realestate.modules.transaction.model.entity.TransactionProcess;
import com.example.realestate.modules.transaction.model.entity.TransactionStatusLog;
import com.example.realestate.modules.transaction.model.vo.TransactionLogVO;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合同 VO 组装：
 *  - 列表补状态/付款周期中文标签；
 *  - 详情房屋信息来自创建时快照（房源后续修改不影响历史合同）；
 *  - 签署方电话对 CUSTOMER 脱敏，ADMIN 看到完整号码用于业务联系。
 */
@Component
public class ContractConvert {

    public ContractListItemVO fillLabels(ContractListItemVO vo) {
        if (vo == null) {
            return null;
        }
        ContractStatus status = ContractStatus.of(vo.getStatus());
        vo.setStatusLabel(status == null ? vo.getStatus() : status.label());
        vo.setPaymentCycleLabel(paymentCycleLabel(vo.getPaymentCycle()));
        if (vo.getPdfReady() == null) {
            vo.setPdfReady(false);
        }
        return vo;
    }

    public ContractPartyVO toPartyVO(ContractParty party, boolean admin) {
        String phone = admin ? party.getPhone() : PhoneMasker.mask(party.getPhone());
        return new ContractPartyVO(party.getPartyType(), partyTypeLabel(party.getPartyType()),
                party.getName(), phone, party.getIdCardMasked());
    }

    public ContractStatusLogVO toLogVO(ContractStatusLog log) {
        return new ContractStatusLogVO(log.getId(),
                log.getFromStatus(), statusLabel(log.getFromStatus()),
                log.getToStatus(), statusLabel(log.getToStatus()),
                log.getOperatorId(), log.getReason(), log.getCreatedAt());
    }

    public TransactionVO toTransactionVO(TransactionProcess process) {
        return new TransactionVO(process.getId(), process.getNode(), nodeLabel(process.getNode()),
                process.getStatus(), transactionStatusLabel(process.getStatus()),
                process.getDoneAt(), process.getRemark(), process.getUpdatedAt());
    }

    public TransactionLogVO toTransactionLogVO(TransactionStatusLog log) {
        return new TransactionLogVO(log.getId(), log.getNode(), nodeLabel(log.getNode()),
                log.getFromStatus(), log.getToStatus(), log.getOperatorId(), log.getReason(),
                log.getCreatedAt());
    }

    public ContractDetailVO toDetail(Contract contract,
                                     ContractSnapshot snapshot,
                                     String customerName,
                                     List<ContractPartyVO> parties,
                                     List<TransactionVO> transactions,
                                     List<ContractStatusLogVO> statusLogs) {
        ContractSnapshot.House house = snapshot == null ? null : snapshot.house();
        ContractSnapshot.Terms terms = snapshot == null ? null : snapshot.terms();
        ContractStatus status = ContractStatus.of(contract.getStatus());
        return new ContractDetailVO(
                contract.getId(),
                contract.getContractNo(),
                contract.getTitle(),
                contract.getHouseId(),
                house == null ? null : house.title(),
                house == null ? null : house.houseType(),
                house == null ? null : house.community(),
                house == null ? null : house.region(),
                house == null ? null : house.address(),
                house == null ? null : house.area(),
                house == null ? null : house.rooms(),
                house == null ? null : house.bathrooms(),
                house == null ? null : house.floor(),
                contract.getCustomerId(),
                customerName,
                contract.getOwnerId(),
                contract.getAmount(),
                contract.getDeposit(),
                terms != null && terms.amountInWords() != null
                        ? terms.amountInWords()
                        : ChineseAmountConverter.toChinese(contract.getAmount()),
                contract.getPaymentCycle(),
                paymentCycleLabel(contract.getPaymentCycle()),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus(),
                status == null ? contract.getStatus() : status.label(),
                contract.getTemplateVersion(),
                contract.getPdfUrl() != null && !contract.getPdfUrl().isBlank(),
                contract.getSignedAt(),
                contract.getVersion(),
                contract.getCreatedAt(),
                contract.getUpdatedAt(),
                parties == null ? List.of() : parties,
                transactions == null ? List.of() : transactions,
                statusLogs == null ? List.of() : statusLogs);
    }

    public String paymentCycleLabel(String cycle) {
        if (cycle == null) {
            return null;
        }
        try {
            return PaymentCycle.valueOf(cycle).label();
        } catch (IllegalArgumentException e) {
            return cycle;
        }
    }

    public String statusLabel(String status) {
        ContractStatus parsed = ContractStatus.of(status);
        return parsed == null ? status : parsed.label();
    }

    public String partyTypeLabel(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ContractParty.TYPE_LESSOR -> "出租方";
            case ContractParty.TYPE_LESSEE -> "承租方";
            case ContractParty.TYPE_AGENT -> "居间方";
            default -> type;
        };
    }

    public String nodeLabel(String node) {
        if (node == null) {
            return null;
        }
        return switch (node) {
            case "DEPOSIT" -> "付定金";
            case "SIGN" -> "签约";
            case "HANDOVER" -> "交房";
            default -> node;
        };
    }

    public String transactionStatusLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待处理";
            case "DONE" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
