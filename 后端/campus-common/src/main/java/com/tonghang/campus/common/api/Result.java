package com.tonghang.campus.common.api;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>来源：《13-API接口文档》§1：
 * <pre>
 * { "code": 0, "msg": "ok", "data": { } }            // 成功
 * { "code": 40004, "msg": "标题不能为空", "data": null } // 失败
 * </pre>
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /* ---------------- 成功 ---------------- */

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMsg(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMsg(), data);
    }

    /* ---------------- 失败 ---------------- */

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getMsg(), null);
    }

    public static <T> Result<T> fail(ErrorCode ec, String msg) {
        return new Result<>(ec.getCode(), msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public boolean isSuccess() {
        return this.code == ErrorCode.OK.getCode();
    }

    /* ---------------- getter / setter ---------------- */

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
