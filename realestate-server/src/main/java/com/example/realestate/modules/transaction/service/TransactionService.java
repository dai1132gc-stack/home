package com.example.realestate.modules.transaction.service;

import com.example.realestate.modules.transaction.model.dto.TransactionDoneRequest;
import com.example.realestate.modules.transaction.model.vo.TransactionLogVO;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;

import java.time.Instant;
import java.util.List;

/**
 * 交易节点最小闭环：合同创建时初始化 DEPOSIT/SIGN/HANDOVER，
 * 生效自动完成 SIGN，其余由 ADMIN 手工确认；合同终止时取消未完成节点。
 */
public interface TransactionService {

    List<TransactionVO> listByContract(Long contractId);

    List<TransactionLogVO> logsByContract(Long contractId);

    /** ADMIN 完成节点（PENDING → DONE），重复完成返回 409 */
    TransactionVO complete(Long contractId, String node, TransactionDoneRequest request);

    /** 合同创建时初始化三个待处理节点（同一事务） */
    void initNodes(Long contractId, Instant now);

    /** 合同生效时自动完成 SIGN 节点 */
    void markSigned(Long contractId);

    /** 合同终止/作废时取消所有未完成节点 */
    void cancelPending(Long contractId, String reason);
}
