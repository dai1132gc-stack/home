package com.example.realestate.modules.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.transaction.model.entity.TransactionProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface TransactionProcessMapper extends BaseMapper<TransactionProcess> {

    /** 按业务节点顺序返回（DEPOSIT → SIGN → HANDOVER） */
    List<TransactionProcess> selectByContract(@Param("contractId") Long contractId);

    /** 条件状态迁移：PENDING → DONE/CANCELLED，返回 0 表示已被处理 */
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target,
                            @Param("doneAt") Instant doneAt,
                            @Param("remark") String remark,
                            @Param("now") Instant now);
}
