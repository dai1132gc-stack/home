package com.example.realestate.modules.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.appointment.model.entity.ViewingAppointment;
import com.example.realestate.modules.appointment.model.query.AppointmentQuery;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 预约 Mapper。
 * 列表为手写 XML：数据权限（forcedCustomerId）、状态、时间范围、分页全部下推 SQL。
 */
@Mapper
public interface ViewingAppointmentMapper extends BaseMapper<ViewingAppointment> {

    List<AppointmentVO> selectPageList(@Param("q") AppointmentQuery query,
                                       @Param("forcedCustomerId") Long forcedCustomerId,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    long countByQuery(@Param("q") AppointmentQuery query,
                      @Param("forcedCustomerId") Long forcedCustomerId);

    AppointmentVO selectVoById(@Param("id") Long id);

    /** 状态机条件更新：仅当库中状态为 expected 才迁移，返回 0 表示并发冲突 */
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expected") String expected,
                            @Param("target") String target,
                            @Param("now") Instant now);

    /** 同一房源仍处于已确认的预约数量（用于释放房源占用） */
    long countConfirmedByHouse(@Param("houseId") Long houseId,
                               @Param("excludeId") Long excludeId);
}
