package com.tonghang.campus.common.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体。
 *
 * <p>来源：《13-API接口文档》§1「分页」：
 * 请求 {@code page}（从 1）/ {@code size}（默认 20，≤50）；响应 {@code data.list / total / page / size}。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> implements Serializable {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list == null ? Collections.emptyList() : list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0L, page, size);
    }

    /* ---------------- getter / setter ---------------- */

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
