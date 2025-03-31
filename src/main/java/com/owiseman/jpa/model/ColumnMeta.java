package com.owiseman.jpa.model;

public record ColumnMeta(
        String name,
        String typeName,
        int length,
        boolean nullable,
        boolean unique,
        String columnDefinition) {

    public boolean hasColumnDefinition() {
        return columnDefinition != null && !columnDefinition.isEmpty();
    }
}
