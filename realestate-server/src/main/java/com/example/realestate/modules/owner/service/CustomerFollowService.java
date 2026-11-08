package com.example.realestate.modules.owner.service;

import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.owner.model.dto.CustomerFollowCreateRequest;
import com.example.realestate.modules.owner.model.vo.CustomerFollowVO;

/** 客源跟进服务：仅 ADMIN；表结构沿用 V2，不新增迁移。 */
public interface CustomerFollowService {

    PageResponse<CustomerFollowVO> list(Long ownerId, PageQuery query);

    CustomerFollowVO create(Long ownerId, CustomerFollowCreateRequest request);
}
