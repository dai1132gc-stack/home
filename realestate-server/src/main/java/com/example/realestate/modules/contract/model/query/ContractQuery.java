package com.example.realestate.modules.contract.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 合同列表查询：状态、房源、客户、关键字全部下推 SQL。
 * customerId 仅 ADMIN 生效；CUSTOMER 由 Service 强制为本人。
 */
@Schema(description = "合同列表查询")
public class ContractQuery extends PageQuery {

    private String status;

    private Long houseId;

    private Long customerId;

    /** 合同编号或标题前缀匹配 */
    private String keyword;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
