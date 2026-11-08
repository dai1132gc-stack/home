package com.example.realestate.common.enums;

/**
 * 稳定业务错误码（与 HTTP 状态对齐，详见技术方案 7.2）。
 */
public enum ErrorCode {

    // 通用
    BAD_REQUEST("COMMON_40001", 400, "请求参数错误"),
    JSON_PARSE_ERROR("COMMON_40002", 400, "请求体格式错误"),
    UNEXPECTED("COMMON_50000", 500, "系统繁忙，请稍后重试"),

    // 认证
    UNAUTHENTICATED("AUTH_40101", 401, "未登录或令牌缺失"),
    TOKEN_INVALID("AUTH_40102", 401, "令牌无效或已过期"),
    LOGIN_FAILED("AUTH_40103", 401, "用户名或密码错误"),
    REFRESH_REPLAY("AUTH_40104", 401, "检测到刷新令牌重放，会话已撤销"),
    FORBIDDEN("AUTH_40301", 403, "无权访问该资源"),
    VALIDATION_FAILED("COMMON_42201", 422, "字段校验失败"),
    NOT_FOUND("COMMON_40401", 404, "资源不存在"),

    // 业务状态
    CONFLICT("COMMON_40901", 409, "当前状态不允许该操作"),
    DUPLICATE("COMMON_40902", 409, "数据已存在，请勿重复提交"),
    RATE_LIMITED("COMMON_42901", 429, "请求过于频繁，请稍后重试"),

    // 文件
    FILE_INVALID("FILE_40001", 400, "文件类型或内容不合法"),
    FILE_TOO_LARGE("FILE_41301", 413, "文件大小超出限制");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
