package com.j111.docqa.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各种异常统一翻译成 {@link Result}，避免把堆栈暴露给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，打 warn 就够 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：把每个字段的错误拼起来返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    /** URL 不存在：这是 404 而不是 500，别让兜底处理器把它误报成服务器故障 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在: " + e.getResourcePath());
    }

    /** 兜底：未预料到的异常，打 error 并返回 500，绝不能把堆栈返回给调用方 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }
}
