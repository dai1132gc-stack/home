package com.example.realestate.modules.owner.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/** 客源列表查询条件（全部下推 SQL） */
@Schema(description = "客源列表查询")
public class OwnerQuery extends PageQuery {

    /** 搜索关键字：纯数字按手机号前缀匹配，否则按姓名前缀匹配（避免 OR 破坏索引） */
    private String keyword;

    /** 由 Service 依据 keyword 推导，不接收客户端直接传值 */
    private String nameKeyword;

    private String phoneKeyword;

    /** LANDLORD / CUSTOMER_LEAD */
    private String type;

    public String getNameKeyword() {
        return nameKeyword;
    }

    public void setNameKeyword(String nameKeyword) {
        this.nameKeyword = nameKeyword;
    }

    public String getPhoneKeyword() {
        return phoneKeyword;
    }

    public void setPhoneKeyword(String phoneKeyword) {
        this.phoneKeyword = phoneKeyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
