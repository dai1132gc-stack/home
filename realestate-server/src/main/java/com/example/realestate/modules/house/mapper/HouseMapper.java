package com.example.realestate.modules.house.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 房源 Mapper。
 * 列表查询为手写 XML：筛选、数据权限 status、排序白名单、LIMIT 全部下推 SQL。
 * 状态联动使用行锁 + 条件更新，保证并发下只有一次迁移成功。
 */
@Mapper
public interface HouseMapper extends BaseMapper<House> {

    List<HouseListItemVO> selectPageList(@Param("q") HouseQuery query,
                                         @Param("forcedStatus") String forcedStatus,
                                         @Param("offset") long offset,
                                         @Param("size") long size);

    long countByQuery(@Param("q") HouseQuery query,
                      @Param("forcedStatus") String forcedStatus);

    /** 事务内锁定房源行：合同激活前串行化同一房源的并发签约 */
    House selectByIdForUpdate(@Param("id") Long id);

    /** 条件更新状态：仅当当前状态在 expectedStatuses 内才迁移，返回影响行数 */
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expectedStatuses") List<String> expectedStatuses,
                            @Param("target") String target,
                            @Param("now") Instant now);
}
