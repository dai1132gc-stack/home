package com.example.realestate.modules.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.query.ContractQuery;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 合同 Mapper：列表条件（数据权限、状态、关键字）与分页全部下推 SQL；
 * 状态迁移使用条件 UPDATE，配合生成列唯一键保证同一房源只有一个占用中的合同。
 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    List<ContractListItemVO> selectPageList(@Param("q") ContractQuery query,
                                            @Param("forcedCustomerId") Long forcedCustomerId,
                                            @Param("offset") long offset,
                                            @Param("size") long size);

    long countByQuery(@Param("q") ContractQuery query,
                      @Param("forcedCustomerId") Long forcedCustomerId);

    ContractListItemVO selectVoById(@Param("id") Long id);

    /** 条件状态迁移：返回 0 表示状态已被并发修改 */
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target,
                            @Param("now") Instant now);

    /** 客户确认生效：PENDING_SIGN → ACTIVE，同时写签署时间与 PDF key */
    int activate(@Param("id") Long id,
                 @Param("signedAt") Instant signedAt,
                 @Param("pdfUrl") String pdfUrl);

    int updatePdfUrl(@Param("id") Long id, @Param("pdfUrl") String pdfUrl, @Param("now") Instant now);

    /** 同一房源占用中的合同数量（PENDING_SIGN/ACTIVE），用于激活前复核 */
    long countOccupyingByHouse(@Param("houseId") Long houseId,
                               @Param("excludeId") Long excludeId);
}
