package com.owiseman.jpa.model;

public record ColumnMeta(
        String name,
        String typeName,
        int length,
        boolean nullable,
        boolean unique,
        String columnDefinition) {
    
    public ColumnMeta(String name, String typeName, int length, boolean nullable, boolean unique) {
        this(name, typeName, length, nullable, unique, "");
    }
    
    public boolean hasColumnDefinition() {
        return columnDefinition != null && !columnDefinition.isEmpty();
    }
}
