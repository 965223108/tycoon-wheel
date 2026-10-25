package com.tycoon.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页返回。
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public PageResult() {
    }

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        PageResult<T> r = new PageResult<>();
        r.items = items;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
