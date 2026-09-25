package edu.sjsu.cmpe172.tutorbook.dto;

import java.util.List;

/** One page of results plus the numbers the UI needs to draw pagination. */
public record PageDto<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    public static <T> PageDto<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / size);
        return new PageDto<>(items, page, size, totalItems, totalPages, page > 0, page + 1 < totalPages);
    }
}
