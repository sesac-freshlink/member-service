package com.freshlink.member.domain.admin;

import java.util.List;
import org.springframework.data.domain.Page;

/** 페이지 형식 (API 명세 0장) */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
