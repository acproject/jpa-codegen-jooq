package com.owiseman.jpa.util;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

public class DSLUtil  {
    public static Field<?> getTableDotFieldName(Table<?> table, Field<?> field) {
        return DSL.field(table.getName() + "." + field.getName());
    }
}
