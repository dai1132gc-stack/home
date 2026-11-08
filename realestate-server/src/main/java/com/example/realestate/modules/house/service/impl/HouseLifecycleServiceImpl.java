package com.example.realestate.modules.house.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseStatus;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 房源状态联动实现：条件 UPDATE（status IN (...)）保证并发下只有一次迁移生效，
 * 失败统一返回 409，不覆盖他人已完成的状态变更。
 */
@Service
public class HouseLifecycleServiceImpl implements HouseLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(HouseLifecycleServiceImpl.class);

    private final HouseMapper houseMapper;

    public HouseLifecycleServiceImpl(HouseMapper houseMapper) {
        this.houseMapper = houseMapper;
    }

    @Override
    public House lock(Long houseId) {
        House house = houseId == null ? null : houseMapper.selectByIdForUpdate(houseId);
        if (house == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在");
        }
        return house;
    }

    @Override
    @Transactional
    public void reserve(Long houseId) {
        int rows = houseMapper.updateStatusIfMatch(houseId,
                List.of(HouseStatus.PUBLISHED.name()), HouseStatus.RESERVED.name(), Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "房源当前不可预定（可能已被预定或下架）");
        }
        log.info("房源状态联动 {}: PUBLISHED -> RESERVED", houseId);
    }

    @Override
    @Transactional
    public void markContracted(Long houseId) {
        int rows = houseMapper.updateStatusIfMatch(houseId,
                List.of(HouseStatus.PUBLISHED.name(), HouseStatus.RESERVED.name()),
                HouseStatus.CONTRACTED.name(), Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "房源当前状态不可签约");
        }
        log.info("房源状态联动 {}: -> CONTRACTED", houseId);
    }

    @Override
    @Transactional
    public boolean release(Long houseId) {
        int rows = houseMapper.updateStatusIfMatch(houseId,
                List.of(HouseStatus.RESERVED.name(), HouseStatus.CONTRACTED.name()),
                HouseStatus.PUBLISHED.name(), Instant.now());
        if (rows > 0) {
            log.info("房源状态联动 {}: -> PUBLISHED", houseId);
        }
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean releaseReservation(Long houseId) {
        int rows = houseMapper.updateStatusIfMatch(houseId,
                List.of(HouseStatus.RESERVED.name()), HouseStatus.PUBLISHED.name(), Instant.now());
        if (rows > 0) {
            log.info("房源状态联动 {}: RESERVED -> PUBLISHED（预约释放）", houseId);
        }
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean releaseContract(Long houseId) {
        int rows = houseMapper.updateStatusIfMatch(houseId,
                List.of(HouseStatus.CONTRACTED.name()), HouseStatus.PUBLISHED.name(), Instant.now());
        if (rows > 0) {
            log.info("房源状态联动 {}: CONTRACTED -> PUBLISHED（合同终止）", houseId);
        }
        return rows > 0;
    }
}
