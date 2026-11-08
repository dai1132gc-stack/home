package com.example.realestate.modules.owner.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.owner.model.dto.OwnerCreateRequest;
import com.example.realestate.modules.owner.model.dto.OwnerUpdateRequest;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.model.query.OwnerQuery;
import com.example.realestate.modules.owner.model.vo.OwnerVO;

public interface OwnerService {

    PageResponse<OwnerVO> list(OwnerQuery query);

    OwnerVO get(Long id);

    OwnerVO create(OwnerCreateRequest request);

    OwnerVO update(Long id, OwnerUpdateRequest request);

    void delete(Long id);

    /** 供房源模块校验 ownerId 合法性 */
    OwnerProfile requireEntity(Long id);

    /** 供房源模块读取房东信息；不存在返回 null（不抛异常） */
    OwnerProfile findEntityOrNull(Long id);

    /** 房源详情展示房东姓名（仅 ADMIN 使用） */
    String findName(Long id);

    /** 批量取姓名，避免列表 N+1 */
    java.util.Map<Long, String> findNames(java.util.Collection<Long> ids);
}
