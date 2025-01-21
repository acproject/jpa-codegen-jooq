package com.owiseman.jpa.util;

import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.impl.DSL;

public class DataAnalysisUtil {
    /**
     * Calculate the total number of records in the table
     * @param dslContext
     * @param tableName
     * @return
     */
    public static int count(DSLContext dslContext, String tableName) {
        return dslContext.selectCount().from(DSL.table(tableName)).fetchOne(0, int.class);
    }

    /**
     * Calculate the sum of a field
     * @param dslContext
     * @param tableName
     * @param columnName
     * @param type
     * @return
     * @param <T>
     */
    public static <T> T sum(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.sum((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    public static <T> T avg(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.avg((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    public static <T> T min(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.min((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    public static <T> T max(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.max((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    public static @NotNull Result<? extends Record2<Object, ?>> groupBy(DSLContext dslContext, String tableName, String groupBy,
                                                                        Field<?> aggregate) {
        return dslContext.select(DSL.field(groupBy), aggregate)
                .from(DSL.table(tableName))
                .groupBy(DSL.field(groupBy))
                .fetch();
    }

    /**
     * 计算字段的方差
     * @param dslContext
     * @param tableName
     * @param columnName
     * @param type
     * @return
     * @param <T>
     */
    public static <T> T variance(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.varPop((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    /**
     * 计算字段的标准差
     * @param dslContext
     * @param tableName
     * @param columnName
     * @param type
     * @return
     * @param <T>
     */
    public static <T> T stddev(DSLContext dslContext, String tableName, String columnName, Class<T> type) {
        return dslContext.select(
                DSL.stddevPop((org.jooq.Field<? extends Number>) DSL.field(columnName, type)))
                .from(DSL.table(tableName)).fetchOne(0, type);
    }

    /**
     * 对查询结果进行分页
     *
     * @param dslContext JOOQ DSLContext
     * @param tableName  表名
     * @param page       页码（从 1 开始）
     * @param pageSize   每页大小
     * @return 分页后的结果
     */
    public static @NotNull Result<Record> paginate(DSLContext dslContext, String tableName, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return dslContext.select()
                .from(tableName)
                .limit(pageSize)
                .offset(offset)
                .fetch();
    }

    /**
     * 根据条件过滤数据
     *
     * @param dslContext JOOQ DSLContext
     * @param tableName  表名
     * @param condition  过滤条件
     * @return 过滤后的结果
     */
    public static @NotNull Result<org.jooq.Record> filter(DSLContext dslContext, String tableName, Condition condition) {
        return dslContext.select()
                .from(tableName)
                .where(condition)
                .fetch();
    }
}
