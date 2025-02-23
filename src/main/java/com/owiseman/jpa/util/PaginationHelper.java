package com.owiseman.jpa.util;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;

public class PaginationHelper {
    /**
     * Select total all pages
     *
     * @param dslContext
     * @param condition
     * @param tableName
     * @param pageSize
     * @return
     */
    public static int getTotalPages(DSLContext dslContext, Condition condition,
                                    String tableName, int pageSize) {
        Condition Con = DSL.noCondition();
        int totalRecords = dslContext
                .selectCount()
                .from(DSL.table(tableName))
                .where(condition)
                .fetchOne(0, int.class);
        return (int) Math.ceil((double) totalRecords / pageSize);
    }

     @SuppressWarnings("unchecked")
    public static <T> List<T> getPaginatedData(DSLContext dslContext,
                                               Condition condition,  // 允许传入 null
                                               String tableName,
                                               int pageSize,
                                               int pageNumber,
                                               Class<T> recordClass) {
        // 处理空条件
        Condition finalCondition = condition != null ? condition : DSL.noCondition();

        return dslContext
                .select()
                .from(DSL.table(tableName))
                .where(finalCondition)
                .limit(pageSize)
                .offset((pageNumber - 1) * pageSize)
                .fetchInto(recordClass);
    }


}
