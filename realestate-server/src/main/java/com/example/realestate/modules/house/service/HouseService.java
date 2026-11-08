package com.example.realestate.modules.house.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.house.model.dto.HouseCreateRequest;
import com.example.realestate.modules.house.model.dto.HouseMediaRequest;
import com.example.realestate.modules.house.model.dto.HouseUpdateRequest;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseDetailVO;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.model.vo.HouseMediaVO;

public interface HouseService {

    /** 分页筛选：非 ADMIN（含游客）强制 status=PUBLISHED，条件下推 SQL */
    PageResponse<HouseListItemVO> list(HouseQuery query);

    /** 详情：非 PUBLISHED 仅 ADMIN 可见，其他人返回 404（避免存在性泄露） */
    HouseDetailVO detail(Long id);

    HouseDetailVO create(HouseCreateRequest request);

    HouseDetailVO update(Long id, HouseUpdateRequest request);

    /** PENDING/OFFLINE → PUBLISHED */
    HouseDetailVO publish(Long id);

    /** PUBLISHED → OFFLINE */
    HouseDetailVO offline(Long id);

    /** 逻辑删除 */
    void delete(Long id);

    HouseMediaVO addMedia(Long houseId, HouseMediaRequest request);

    void removeMedia(Long houseId, Long mediaId);
}
