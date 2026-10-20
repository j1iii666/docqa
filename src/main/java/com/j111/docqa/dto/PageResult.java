package com.j111.docqa.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 通用分页结果。
 * <p>
 * 不直接把 Spring 的 Page 返回给前端：它的 JSON 结构臃肿（带 pageable、sort 等一堆字段），
 * 而且和具体框架绑死了，以后换 ORM 就得改接口。
 *
 * @param page 当前页码，从 1 开始
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public static <E, T> PageResult<T> of(Page<E> source, java.util.function.Function<E, T> mapper) {
        return new PageResult<>(
                source.getContent().stream().map(mapper).toList(),
                source.getTotalElements(),
                source.getNumber() + 1,
                source.getSize());
    }
}
