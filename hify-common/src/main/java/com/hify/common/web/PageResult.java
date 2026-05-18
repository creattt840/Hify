package com.hify.common.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageResult<T> extends Result<T> {

    private long total;
    private int page;
    private int size;

    private PageResult(long total, int page, int size, T data) {
        super(200, "success", data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(long total, int page, int size, T data) {
        return new PageResult<>(total, page, size, data);
    }
}
