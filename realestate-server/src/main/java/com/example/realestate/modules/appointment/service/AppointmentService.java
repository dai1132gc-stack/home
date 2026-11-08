package com.example.realestate.modules.appointment.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.appointment.model.dto.AppointmentCreateRequest;
import com.example.realestate.modules.appointment.model.query.AppointmentQuery;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;

/**
 * 预约带看服务。数据权限：ADMIN 全部，CUSTOMER 仅本人，游客不可访问（接口层已要求认证）。
 */
public interface AppointmentService {

    PageResponse<AppointmentVO> list(AppointmentQuery query);

    AppointmentVO detail(Long id);

    /** 客户提交预约：房源必须为 PUBLISHED */
    AppointmentVO create(AppointmentCreateRequest request);

    /** ADMIN 确认：PENDING → CONFIRMED，房源 PUBLISHED → RESERVED */
    AppointmentVO confirm(Long id);

    /** 客户取消本人预约 / ADMIN 取消：PENDING/CONFIRMED → CANCELLED */
    AppointmentVO cancel(Long id);

    /** ADMIN 标记完成：CONFIRMED → COMPLETED */
    AppointmentVO complete(Long id);

    /** ADMIN 标记爽约：CONFIRMED → NO_SHOW */
    AppointmentVO noShow(Long id);
}
