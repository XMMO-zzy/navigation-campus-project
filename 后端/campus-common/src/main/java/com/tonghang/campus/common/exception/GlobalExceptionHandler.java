package com.tonghang.campus.common.exception;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：任何异常都转为统一 {@link Result}，HTTP 状态码保持 200。
 *
 * <p>来源：《11-系统概要设计文档》§1.3；错误码语义见《13》§2。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("[BizException] {} {} -> {}({})",
                req.getMethod(), req.getRequestURI(), e.getMessage(), e.getErrorCode().getCode());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    /** @Valid 校验失败（RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg.isEmpty() ? ErrorCode.PARAM_INVALID.getMsg() : msg);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ErrorCode.PARAM_INVALID, msg.isEmpty() ? ErrorCode.PARAM_INVALID.getMsg() : msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求体不可读 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    /** 方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ErrorCode.NOT_FOUND, "请求方法不支持：" + e.getMethod());
    }

    /** 路由不存在 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND, "接口不存在：" + e.getRequestURL());
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOthers(Exception e, HttpServletRequest req) {
        log.error("[UnhandledException] {} {}", req.getMethod(), req.getRequestURI(), e);
        return Result.fail(ErrorCode.SERVER_ERROR);
    }
}
