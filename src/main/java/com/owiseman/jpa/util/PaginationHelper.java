package com.owiseman.jpa.util;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class PaginationHelper {
    /**
     * Select total all pages
     * @param dslContext
     * @param condition
     * @param tableName
     * @param pageSize
     * @return
     */
    public static int getTotalPages(DSLContext dslContext, Condition condition,
                                      String tableName, int pageSize){
        int totalRecords = dslContext
                .selectCount()
                .from(DSL.table(tableName))
                .where(condition)
                .fetchOne(0, int.class);
        return (int) Math.ceil((double) totalRecords / pageSize);
    }
}
