package com.owiseman.jpa.model;

public record ForeignKey(
        String name,
        String column,
        String refTable,
        String refColumn
) {
}
