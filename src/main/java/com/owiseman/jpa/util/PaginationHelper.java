package com.owiseman.jpa.util;

import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SortField;
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

        if (pageSize <= 0) {
            pageSize = 1;
        }
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
        if (pageSize <= 0) {
            pageSize = 1;
        }
        if (pageNumber <= 0) {
            pageNumber = 1;
        }
        return dslContext
                .select()
                .from(DSL.table(tableName))
                .where(finalCondition)
                .limit(pageSize)
                .offset((pageNumber - 1) * pageSize)
                .fetchInto(recordClass);
    }

     public static <T> List<T> getPaginatedData(DSLContext dsl,
                                              Condition condition,
                                              String tableName,
                                              int pageSize,
                                              int pageNumber,
                                              Class<T> entityClass,
                                              SortField<?>... sortFields) { // 新增排序参数
        if (pageSize <= 0) {
            pageSize = 1;
        }
        if (pageNumber <= 0) {
            pageNumber = 1;
        }
        return dsl.selectFrom(DSL.table(tableName))
            .where(condition)
            .orderBy(sortFields) // 应用排序
            .limit(pageSize)
            .offset((pageNumber - 1) * pageSize)
            .fetchInto(entityClass);
    }

    public static @NotNull Result<Record> getPaginatedDataToResult(DSLContext dsl,
                                                                   Condition condition,
                                                                   String tableName,
                                                                   int pageSize,
                                                                   int pageNumber,
                                                                   SortField<?>... sortFields) { // 新增排序参数
        if (pageSize <= 0) {
            pageSize = 1;
        }
        if (pageNumber <= 0) {
            pageNumber = 1;
        }
        return dsl.selectFrom(DSL.table(tableName))
            .where(condition)
            .orderBy(sortFields) // 应用排序
            .limit(pageSize)
            .offset((pageNumber - 1) * pageSize)
            .fetch();
    }


}
