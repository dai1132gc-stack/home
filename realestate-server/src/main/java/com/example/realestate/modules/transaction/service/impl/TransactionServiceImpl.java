package com.example.realestate.modules.transaction.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.TransactionNode;
import com.example.realestate.common.enums.TransactionStatus;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.contract.convert.ContractConvert;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.transaction.mapper.TransactionProcessMapper;
import com.example.realestate.modules.transaction.mapper.TransactionStatusLogMapper;
import com.example.realestate.modules.transaction.model.dto.TransactionDoneRequest;
import com.example.realestate.modules.transaction.model.entity.TransactionProcess;
import com.example.realestate.modules.transaction.model.entity.TransactionStatusLog;
import com.example.realestate.modules.transaction.model.vo.TransactionLogVO;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import com.example.realestate.modules.transaction.service.TransactionService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 交易节点实现：状态迁移使用条件 UPDATE，重复完成/并发完成只有一个成功，其余 409。
 * 数据权限：ADMIN 全部；CUSTOMER 仅本人合同（他人 404）。
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionProcessMapper processMapper;
    private final TransactionStatusLogMapper logMapper;
    private final ContractMapper contractMapper;
    private final ContractConvert contractConvert;
    private final CurrentUserProvider currentUser;

    public TransactionServiceImpl(TransactionProcessMapper processMapper,
                                  TransactionStatusLogMapper logMapper,
                                  ContractMapper contractMapper,
                                  ContractConvert contractConvert,
                                  CurrentUserProvider currentUser) {
        this.processMapper = processMapper;
        this.logMapper = logMapper;
        this.contractMapper = contractMapper;
        this.contractConvert = contractConvert;
        this.currentUser = currentUser;
    }

    @Override
    public List<TransactionVO> listByContract(Long contractId) {
        requireVisibleContract(contractId);
        return processMapper.selectByContract(contractId).stream()
                .map(contractConvert::toTransactionVO)
                .toList();
    }

    @Override
    public List<TransactionLogVO> logsByContract(Long contractId) {
        requireVisibleContract(contractId);
        return logMapper.selectList(Wrappers.<TransactionStatusLog>lambdaQuery()
                        .eq(TransactionStatusLog::getContractId, contractId)
                        .orderByDesc(TransactionStatusLog::getCreatedAt)
                        .orderByDesc(TransactionStatusLog::getId))
                .stream()
                .map(contractConvert::toTransactionLogVO)
                .toList();
    }

    @Override
    @Transactional
    public TransactionVO complete(Long contractId, String node, TransactionDoneRequest request) {
        Contract contract = requireVisibleContract(contractId);
        if (!ContractStatus.ACTIVE.name().equals(contract.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "合同未生效，不能办理交易节点");
        }
        TransactionNode parsedNode = TransactionNode.of(node);
        if (parsedNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "交易节点不合法");
        }
        TransactionProcess process = processMapper.selectOne(Wrappers.<TransactionProcess>lambdaQuery()
                .eq(TransactionProcess::getContractId, contractId)
                .eq(TransactionProcess::getNode, parsedNode.name()));
        if (process == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "交易节点不存在");
        }
        if (!TransactionStatus.PENDING.name().equals(process.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该节点已处理，不能重复完成");
        }
        Instant now = Instant.now();
        String remark = request == null || request.remark() == null || request.remark().isBlank()
                ? null : request.remark().trim();
        int rows = processMapper.updateStatusIfMatch(process.getId(),
                TransactionStatus.PENDING.name(), TransactionStatus.DONE.name(), now, remark, now);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "交易节点已被他人处理，请刷新后重试");
        }
        writeLog(contractId, parsedNode.name(), TransactionStatus.PENDING.name(),
                TransactionStatus.DONE.name(), remark);
        log.info("交易节点完成 contractId={} node={} operator={}", contractId, parsedNode,
                currentUser.currentUserIdOrNull());
        return contractConvert.toTransactionVO(processMapper.selectById(process.getId()));
    }

    @Override
    @Transactional
    public void initNodes(Long contractId, Instant now) {
        for (TransactionNode node : TransactionNode.values()) {
            TransactionProcess process = new TransactionProcess();
            process.setContractId(contractId);
            process.setNode(node.name());
            process.setStatus(TransactionStatus.PENDING.name());
            process.setCreatedAt(now);
            process.setUpdatedAt(now);
            processMapper.insert(process);
            writeLog(contractId, node.name(), null, TransactionStatus.PENDING.name(), "合同起草初始化");
        }
    }

    @Override
    @Transactional
    public void markSigned(Long contractId) {
        TransactionProcess sign = processMapper.selectOne(Wrappers.<TransactionProcess>lambdaQuery()
                .eq(TransactionProcess::getContractId, contractId)
                .eq(TransactionProcess::getNode, TransactionNode.SIGN.name()));
        if (sign == null || !TransactionStatus.PENDING.name().equals(sign.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        int rows = processMapper.updateStatusIfMatch(sign.getId(),
                TransactionStatus.PENDING.name(), TransactionStatus.DONE.name(), now, "合同生效自动完成", now);
        if (rows > 0) {
            writeLog(contractId, TransactionNode.SIGN.name(), TransactionStatus.PENDING.name(),
                    TransactionStatus.DONE.name(), "合同生效自动完成");
        }
    }

    @Override
    @Transactional
    public void cancelPending(Long contractId, String reason) {
        List<TransactionProcess> pending = processMapper.selectList(Wrappers.<TransactionProcess>lambdaQuery()
                .eq(TransactionProcess::getContractId, contractId)
                .eq(TransactionProcess::getStatus, TransactionStatus.PENDING.name()));
        Instant now = Instant.now();
        for (TransactionProcess process : pending) {
            int rows = processMapper.updateStatusIfMatch(process.getId(),
                    TransactionStatus.PENDING.name(), TransactionStatus.CANCELLED.name(), null, reason, now);
            if (rows > 0) {
                writeLog(contractId, process.getNode(), TransactionStatus.PENDING.name(),
                        TransactionStatus.CANCELLED.name(), reason);
            }
        }
    }

    private Contract requireVisibleContract(Long contractId) {
        Contract contract = contractId == null ? null : contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
        }
        if (!currentUser.isAdmin()) {
            Long userId = currentUser.requireUserId();
            if (!userId.equals(contract.getCustomerId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
            }
        }
        return contract;
    }

    private void writeLog(Long contractId, String node, String from, String to, String reason) {
        TransactionStatusLog statusLog = new TransactionStatusLog();
        statusLog.setContractId(contractId);
        statusLog.setNode(node);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setOperatorId(currentUser.currentUserIdOrNull());
        statusLog.setReason(reason);
        statusLog.setCreatedAt(Instant.now());
        logMapper.insert(statusLog);
    }
}
