package com.tonghang.campus.common.exception;

import com.tonghang.campus.common.api.ErrorCode;

/**
 * 业务异常：携带全局错误码，由 GlobalExceptionHandler 统一转为 Result。
 *
 * <p>来源：《11-系统概要设计文档》§1.3「Controller 统一 Result&lt;T&gt;，错误码见《13》§2」。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    /** 覆盖默认 msg（例如携带字段级校验说明） */
    public BizException(ErrorCode errorCode, String msg) {
        super(msg);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String msg, Throwable cause) {
        super(msg, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /* ---------------- 语义化静态工厂 ---------------- */

    public static BizException of(ErrorCode ec) {
        return new BizException(ec);
    }

    public static BizException of(ErrorCode ec, String msg) {
        return new BizException(ec, msg);
    }

    public static BizException param(String msg) {
        return new BizException(ErrorCode.PARAM_INVALID, msg);
    }

    public static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND);
    }

    public static BizException notFound(String msg) {
        return new BizException(ErrorCode.NOT_FOUND, msg);
    }

    public static BizException forbidden() {
        return new BizException(ErrorCode.FORBIDDEN);
    }

    public static BizException forbidden(String msg) {
        return new BizException(ErrorCode.FORBIDDEN, msg);
    }

    public static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static BizException duplicate() {
        return new BizException(ErrorCode.DUPLICATE);
    }

    public static BizException duplicate(String msg) {
        return new BizException(ErrorCode.DUPLICATE, msg);
    }

    public static BizException conflict() {
        return new BizException(ErrorCode.STATUS_CONFLICT);
    }

    public static BizException conflict(String msg) {
        return new BizException(ErrorCode.STATUS_CONFLICT, msg);
    }
}
