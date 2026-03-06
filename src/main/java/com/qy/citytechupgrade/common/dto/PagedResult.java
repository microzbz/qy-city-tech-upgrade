package com.qy.citytechupgrade.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResult<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;

    public static <T> PagedResult<T> of(List<T> records, long total, int page, int size) {
        return new PagedResult<>(records, total, page, size);
    }
}
