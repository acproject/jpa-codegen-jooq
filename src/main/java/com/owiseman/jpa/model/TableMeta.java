package com.owiseman.jpa.model;

import java.util.List;

public record TableMeta(
        String name,
        List<ColumnMeta> columns,
        List<String> primaryKey,
        List<Index> indexes,
        List<ForeignKey> foreignKeys,
        boolean generateJooq
) {
}
