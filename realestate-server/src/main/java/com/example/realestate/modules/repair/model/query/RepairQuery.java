package com.example.realestate.modules.repair.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 报修列表查询：状态/分类/关键字/时间范围全部下推 SQL。 */
@Schema(description = "报修列表查询")
public class RepairQuery extends PageQuery {

    private String status;

    private String category;

    /** 单号或标题前缀匹配 */
    private String keyword;

    private Instant fromTime;

    private Instant toTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Instant getFromTime() {
        return fromTime;
    }

    public void setFromTime(Instant fromTime) {
        this.fromTime = fromTime;
    }

    public Instant getToTime() {
        return toTime;
    }

    public void setToTime(Instant toTime) {
        this.toTime = toTime;
    }
}
