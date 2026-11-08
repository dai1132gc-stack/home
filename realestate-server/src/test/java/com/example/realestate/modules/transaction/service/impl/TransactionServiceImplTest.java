package com.example.realestate.modules.transaction.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.contract.convert.ContractConvert;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.transaction.mapper.TransactionProcessMapper;
import com.example.realestate.modules.transaction.mapper.TransactionStatusLogMapper;
import com.example.realestate.modules.transaction.model.dto.TransactionDoneRequest;
import com.example.realestate.modules.transaction.model.entity.TransactionProcess;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import com.example.realestate.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("交易节点：状态约束与重复完成")
class TransactionServiceImplTest {

    @Mock
    private TransactionProcessMapper processMapper;
    @Mock
    private TransactionStatusLogMapper logMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private CurrentUserProvider currentUser;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(processMapper, logMapper, contractMapper,
                new ContractConvert(), currentUser);
    }

    private Contract contract(String status) {
        Contract contract = new Contract();
        contract.setId(5L);
        contract.setCustomerId(2L);
        contract.setHouseId(7L);
        contract.setStatus(status);
        return contract;
    }

    private TransactionProcess process(String status) {
        TransactionProcess process = new TransactionProcess();
        process.setId(9L);
        process.setContractId(5L);
        process.setNode("DEPOSIT");
        process.setStatus(status);
        process.setCreatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        process.setUpdatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        return process;
    }

    @Test
    @DisplayName("合同未生效不能办理交易节点")
    void cannotCompleteBeforeActive() {
        when(contractMapper.selectById(5L)).thenReturn(contract("PENDING_SIGN"));
        when(currentUser.isAdmin()).thenReturn(true);

        assertThatThrownBy(() -> service.complete(5L, "DEPOSIT", new TransactionDoneRequest("收定金")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(processMapper, never()).updateStatusIfMatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("节点已完成再次办理返回 409")
    void cannotCompleteTwice() {
        when(contractMapper.selectById(5L)).thenReturn(contract("ACTIVE"));
        when(currentUser.isAdmin()).thenReturn(true);
        when(processMapper.selectOne(any())).thenReturn(process("DONE"));

        assertThatThrownBy(() -> service.complete(5L, "DEPOSIT", new TransactionDoneRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("完成节点：条件更新成功并写日志")
    void completesNode() {
        when(contractMapper.selectById(5L)).thenReturn(contract("ACTIVE"));
        when(currentUser.isAdmin()).thenReturn(true);
        when(currentUser.currentUserIdOrNull()).thenReturn(1L);
        when(processMapper.selectOne(any())).thenReturn(process("PENDING"));
        when(processMapper.updateStatusIfMatch(eq(9L), eq("PENDING"), eq("DONE"), any(), eq("定金到账"), any()))
                .thenReturn(1);
        when(processMapper.selectById(9L)).thenReturn(process("DONE"));

        TransactionVO vo = service.complete(5L, "DEPOSIT", new TransactionDoneRequest("定金到账"));

        assertThat(vo.status()).isEqualTo("DONE");
        verify(logMapper).insert(any(com.example.realestate.modules.transaction.model.entity.TransactionStatusLog.class));
    }

    @Test
    @DisplayName("并发完成：条件更新 0 行返回 409")
    void concurrentCompleteConflicts() {
        when(contractMapper.selectById(5L)).thenReturn(contract("ACTIVE"));
        when(currentUser.isAdmin()).thenReturn(true);
        when(processMapper.selectOne(any())).thenReturn(process("PENDING"));
        when(processMapper.updateStatusIfMatch(anyLong(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(5L, "DEPOSIT", new TransactionDoneRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("初始化：三个节点各写一条待处理记录与日志")
    void initNodesCreatesThree() {
        service.initNodes(5L, Instant.now());

        verify(processMapper, org.mockito.Mockito.times(3)).insert(any(TransactionProcess.class));
        verify(logMapper, org.mockito.Mockito.times(3)).insert(any(com.example.realestate.modules.transaction.model.entity.TransactionStatusLog.class));
    }

    @Test
    @DisplayName("合同终止：未完成节点全部取消")
    void cancelPending() {
        when(processMapper.selectList(any())).thenReturn(List.of(process("PENDING")));
        when(processMapper.updateStatusIfMatch(eq(9L), eq("PENDING"), eq("CANCELLED"), any(), any(), any()))
                .thenReturn(1);

        service.cancelPending(5L, "协商解除");

        verify(logMapper).insert(any(com.example.realestate.modules.transaction.model.entity.TransactionStatusLog.class));
    }

    @Test
    @DisplayName("客户访问他人合同节点返回 404")
    void othersContractHidden() {
        Contract contract = contract("ACTIVE");
        contract.setCustomerId(99L);
        when(contractMapper.selectById(5L)).thenReturn(contract);
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);

        assertThatThrownBy(() -> service.listByContract(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
