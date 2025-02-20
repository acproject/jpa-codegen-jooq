package com.owiseman.jpa.util;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class JooqDdlGeneratorUtil {

    public static List<String> generateTables(Class<?> tablesClass) {
        List<String> ddls = new ArrayList<>();

        // 遍历所有内部类
        for (Class<?> innerClass : tablesClass.getDeclaredClasses()) {
            try {
                // 检查是否包含TABLE字段
                if (innerClass.getDeclaredField("TABLE") != null) {
                    ddls.add(generateTableDDL(innerClass));
                }
            } catch (NoSuchFieldException e) {
                // 忽略没有TABLE字段的类
            }
        }
        return ddls;
    }

    private static String generateTableDDL(Class<?> tableClass) {
        try {
            // 获取表名
            Table<?> table = (Table<?>) tableClass.getDeclaredField("TABLE").get(null);
            String tableName = table.getName();

            // 收集字段定义
            List<String> columns = new ArrayList<>();
            for (java.lang.reflect.Field field : tableClass.getDeclaredFields()) {
                if (field.getName().equals("TABLE")) continue;

                if (Modifier.isPublic(field.getModifiers()) &&
                    Modifier.isStatic(field.getModifiers()) &&
                    Field.class.isAssignableFrom(field.getType())) {

                    Field<?> column = (Field<?>) field.get(null);
                    String columnName = column.getName();
                    DataType<?> dataType = column.getDataType();
                    boolean nullable = column.getDataType().nullable();

                    columns.add(String.format("%s %s %s",
                        columnName,
                        mapDataType(dataType),
                        nullable ? "NULL" : "NOT NULL"));
                }
            }

            // 构建DDL语句
            return String.format("CREATE TABLE %s (\n    %s\n);",
                tableName,
                String.join(",\n    ", columns));

        } catch (Exception e) {
            throw new RuntimeException("Generate DDL failed for: " + tableClass, e);
        }
    }

    private static String mapDataType(DataType<?> dataType) {
        // 根据实际需要扩展类型映射
        if (dataType == SQLDataType.VARCHAR) {
            return "VARCHAR(255)";
        } else if (dataType == SQLDataType.INTEGER) {
            return "INT";
        } else if (dataType == SQLDataType.BOOLEAN) {
            return "BOOLEAN";
        } else if (dataType == SQLDataType.LOCALDATETIME) {
            return "DATETIME";
        } else if (dataType == SQLDataType.BIGINT) {
            return "BIGINT";
        } else if (dataType == SQLDataType.OTHER) {
            return "TEXT";
        }
        return "TEXT";
    }

//    public static void main(String[] args) {
//        // 使用示例
//        List<String> ddls = generateTables(Tables.class);
//        ddls.forEach(System.out::println);
//    }
}
