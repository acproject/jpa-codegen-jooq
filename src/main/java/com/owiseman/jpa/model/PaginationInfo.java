package com.owiseman.jpa.model;

public record PaginationInfo(
        int currentPage,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
