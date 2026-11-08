package com.example.realestate.modules.user.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/** 客户账号查询：账号/昵称前缀匹配，仅 ADMIN 使用 */
@Schema(description = "客户账号查询")
public class CustomerQuery extends PageQuery {

    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
