package com.example.realestate.modules.house.service;

import com.example.realestate.modules.house.model.entity.House;

/**
 * 房源状态联动服务：预约确认 → RESERVED，合同生效 → CONTRACTED，合同终止/预约释放 → PUBLISHED。
 * 只负责状态迁移与行锁，业务前置条件（是否仍有有效预约/合同）由预约、合同 Service 判断。
 */
public interface HouseLifecycleService {

    /** 事务内锁定房源行；不存在返回 404 */
    House lock(Long houseId);

    /** PUBLISHED → RESERVED；房源不在可预定状态返回 409 */
    void reserve(Long houseId);

    /** PUBLISHED/RESERVED → CONTRACTED；房源不可签约返回 409 */
    void markContracted(Long houseId);

    /** RESERVED/CONTRACTED → PUBLISHED；返回是否实际发生迁移 */
    boolean release(Long houseId);

    /** 仅释放预约占用：RESERVED → PUBLISHED（不影响已签约房源） */
    boolean releaseReservation(Long houseId);

    /** 仅释放合同占用：CONTRACTED → PUBLISHED */
    boolean releaseContract(Long houseId);
}
