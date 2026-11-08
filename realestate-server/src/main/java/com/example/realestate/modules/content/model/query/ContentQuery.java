package com.example.realestate.modules.content.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/** 内容/政策列表查询：状态下推 SQL，公开访问时由 Service 强制 PUBLISHED。 */
@Schema(description = "内容列表查询")
public class ContentQuery extends PageQuery {

    private String category;

    /** 仅 ADMIN 生效；公开访问强制 PUBLISHED */
    private String status;

    /** 标题前缀匹配 */
    private String keyword;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
