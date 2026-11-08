package com.example.realestate.common.exception;

import com.example.realestate.common.enums.ErrorCode;

/**
 * 类型化业务异常，由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String message() {
        return customMessage != null ? customMessage : errorCode.defaultMessage();
    }
}
