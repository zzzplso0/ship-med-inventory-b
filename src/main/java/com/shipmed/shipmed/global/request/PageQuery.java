package com.shipmed.shipmed.global.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


public record PageQuery(
        @Min(0) @Max(PageQuery.MAX_PAGE) Integer page,
        @Min(1) @Max(PageQuery.MAX_SIZE) Integer size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

    public PageQuery{
        page = (page == null) ? DEFAULT_PAGE : page;
        size = (size == null) ? DEFAULT_SIZE : size;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by("id"));
    }
}
