package com.artfetch.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public class PageResult<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        PageResult<T> result = new PageResult<>();
        result.setItems(page.getContent().stream().map(mapper).collect(Collectors.toList()));
        result.setTotal(page.getTotalElements());
        result.setPage(page.getNumber());
        result.setSize(page.getSize());
        result.setTotalPages(page.getTotalPages());
        return result;
    }
}
