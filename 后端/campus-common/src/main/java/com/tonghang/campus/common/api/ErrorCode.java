package com.tonghang.campus.common.api;

/**
 * 全局错误码。
 *
 * <p>来源：《13-API接口文档》§2「错误码（全局唯一，维护于 common 包 ErrorCode）」。
 * <p>约定：code=0 表示成功；4xxxx 为客户端可处理错误；5xxxx 为服务端/依赖错误。
 */
public enum ErrorCode {

    /** 成功 */
    OK(0, "ok"),

    /* ---------------- 4xxxx 客户端 ---------------- */
    UNAUTHORIZED(40001, "未登录或登录已失效"),
    TOKEN_EXPIRED(40002, "登录状态已过期"),
    FORBIDDEN(40003, "权限不足，需完成学号认证"),
    PARAM_INVALID(40004, "参数校验失败"),
    NOT_FOUND(40005, "资源不存在"),
    DUPLICATE(40006, "请勿重复操作"),
    STATUS_CONFLICT(40007, "当前状态不允许该操作"),
    SENSITIVE_WORD(40008, "内容含违规信息，请修改后重试"),
    RATE_LIMIT(40009, "操作太频繁，请稍后再试"),
    FILE_ILLEGAL(40010, "文件类型或大小不符合要求"),

    VERIFY_PENDING(41001, "学号认证审核中"),
    VERIFY_REJECTED(41002, "学号认证未通过"),

    WX_API_FAILED(42001, "微信授权调用失败，请重试"),

    /* ---------------- 5xxxx 服务端 ---------------- */
    SERVER_ERROR(50000, "服务开小差了，请稍后再试"),
    THIRD_PARTY_ERROR(50001, "第三方服务异常");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
