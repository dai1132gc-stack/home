package com.example.realestate.common.exception;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理：生产响应不暴露堆栈、SQL、路径与密钥。
 * 未预期异常仅记录服务端日志（关联 traceId），客户端只获得安全提示。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation 字段校验失败 → 422 + errors */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(
                                fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                                Collectors.toList())));
        return build(ErrorCode.VALIDATION_FAILED, errors);
    }

    /** JSON 解析失败 → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());
        return build(ErrorCode.JSON_PARSE_ERROR, null);
    }

    /** 参数类型不匹配 → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.BAD_REQUEST, null);
    }

    /** 缺少必填参数 / 上传文件缺失 → 400（避免落到 500 兜底） */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(Exception ex) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        return build(ErrorCode.BAD_REQUEST, null);
    }

    /** 上传文件超出容器限制 → 413 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return build(ErrorCode.FILE_TOO_LARGE, null);
    }

    /** 未认证 / 令牌无效 → 401（统一提示，不区分令牌缺失与过期细节之外的内部原因） */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.TOKEN_INVALID, null);
    }

    /** 已登录但无权限 → 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, null);
    }

    /** 资源不存在 → 404 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return build(ErrorCode.NOT_FOUND, null);
    }

    /** 业务异常 → 对应 HTTP 状态 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.errorCode();
        if (ec.httpStatus() >= 500) {
            log.error("Business error [{}]: {}", ec.code(), ex.getMessage(), ex);
        } else {
            log.warn("Business error [{}]: {}", ec.code(), ex.getMessage());
        }
        return ResponseEntity.status(ec.httpStatus())
                .body(ApiResponse.error(ec.code(), ex.message()));
    }

    /** 兜底：未预期异常 → 500，不暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String user = SecurityContextHolder.getContext().getAuthentication() != null
                ? String.valueOf(SecurityContextHolder.getContext().getAuthentication().getName())
                : "anonymous";
        log.error("Unexpected error on {} {} user={} type={}",
                request.getMethod(), request.getRequestURI(), user, ex.getClass().getName(), ex);
        return build(ErrorCode.UNEXPECTED, null);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode ec, Map<String, List<String>> errors) {
        return ResponseEntity.status(HttpStatus.valueOf(ec.httpStatus()))
                .body(errors == null
                        ? ApiResponse.error(ec.code(), ec.defaultMessage())
                        : ApiResponse.error(ec.code(), ec.defaultMessage(), errors));
    }
}
