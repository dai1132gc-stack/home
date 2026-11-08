package com.example.realestate.common.api;

/**
 * 分页请求基类：page 从 1 开始，size 上限 100。
 * 偏移量统一由服务端计算，客户端不能传 offset，避免越界与超深分页。
 */
public class PageQuery {

    private static final long MAX_SIZE = 100L;

    private long page = 1L;

    private long size = 10L;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    /** 规范化页码：最小 1 */
    public long normalizedPage() {
        return page < 1 ? 1 : page;
    }

    /** 规范化每页条数：1 ～ 100 */
    public long normalizedSize() {
        if (size < 1) {
            return 10L;
        }
        return Math.min(size, MAX_SIZE);
    }

    public long offset() {
        return (normalizedPage() - 1) * normalizedSize();
    }
}
