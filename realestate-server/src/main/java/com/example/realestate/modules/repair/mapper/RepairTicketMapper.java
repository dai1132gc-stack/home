package com.example.realestate.modules.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.repair.model.entity.RepairTicket;
import com.example.realestate.modules.repair.model.query.RepairQuery;
import com.example.realestate.modules.repair.model.vo.RepairDetailRow;
import com.example.realestate.modules.repair.model.vo.RepairListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 报修工单 Mapper：筛选、分页与数据权限（forcedCustomerId）全部下推 SQL；
 * 状态迁移使用条件 UPDATE，0 行即表示并发冲突（409）。
 */
@Mapper
public interface RepairTicketMapper extends BaseMapper<RepairTicket> {

    List<RepairListRow> selectPageList(@Param("q") RepairQuery query,
                                       @Param("forcedCustomerId") Long forcedCustomerId,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    long countByQuery(@Param("q") RepairQuery query,
                      @Param("forcedCustomerId") Long forcedCustomerId);

    RepairDetailRow selectDetailById(@Param("id") Long id);

    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target,
                            @Param("now") Instant now);
}
