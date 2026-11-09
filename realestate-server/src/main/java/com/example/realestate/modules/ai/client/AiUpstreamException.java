package com.example.realestate.modules.ai.client;

/** AI 上游调用失败（超时/429/5xx/连接错误）。kind 用于区分降级口径。 */
public class AiUpstreamException extends RuntimeException {

    public enum Kind {
        TIMEOUT, RATE_LIMITED, SERVER_ERROR, UNKNOWN
    }

    private final Kind kind;

    public AiUpstreamException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiUpstreamException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
