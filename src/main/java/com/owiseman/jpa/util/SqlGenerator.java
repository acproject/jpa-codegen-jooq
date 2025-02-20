package com.owiseman.jpa.util;

import com.owiseman.jpa.model.ColumnMeta;
import com.owiseman.jpa.model.DataSourceEnum;
import com.owiseman.jpa.model.TableMeta;

import java.util.ArrayList;
import java.util.List;

public class SqlGenerator implements MapToType {
    private final List<String> ddlStatements = new ArrayList<>();

    public void addTable(TableMeta table, DataSourceEnum dataSourceEnum) {
        ddlStatements.add(buildCreateTableStatement(table, dataSourceEnum));
    }

    private String buildCreateTableStatement(TableMeta table, DataSourceEnum dataSourceEnum) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table.name()).append(" (\n");

        // 处理字段定义
        table.columns().forEach(col -> {
            String typeDef = mapToType(col, dataSourceEnum.POSTGRESQL);
            // 添加唯一约束
            String uniqueConstraint = col.unique() ? " UNIQUE" : "";
            sb.append("  ").append(col.name()).append(" ")
                    .append(typeDef).append(uniqueConstraint)
                    .append(",\n");
        });

        // 处理主键
        if (!table.primaryKey().isEmpty()) {
            sb.append("  PRIMARY KEY (").append(String.join(", ", table.primaryKey())).append("),\n");
        }

        // 移除末尾逗号
        if (sb.charAt(sb.length() - 2) == ',') {
            sb.delete(sb.length() - 2, sb.length());
        }

        sb.append(");\n");

        // 添加索引和外键
        table.indexes().forEach(idx ->
                sb.append("CREATE INDEX ").append(idx.name()).append(" ON ")
                        .append(table.name()).append(" (").append(String.join(", ", idx.columns())).append(");\n")
        );

        table.foreignKeys().forEach(fk ->
                sb.append("ALTER TABLE ").append(table.name())
                        .append(" ADD CONSTRAINT ").append(fk.name())
                        .append(" FOREIGN KEY (").append(fk.column())
                        .append(") REFERENCES ").append(fk.refTable())
                        .append(" (").append(fk.refColumn()).append(");\n")
        );

        return sb.toString();
    }

    @Override
    public String mapToType(ColumnMeta col, DataSourceEnum dataSourceEnum) {
        String baseType = switch (dataSourceEnum) {
            case POSTGRESQL -> {
                if (isEnumType(col.typeName())) {
                    yield "VARCHAR(255)";
                }

                if (isCollectionOrMapType(col.typeName())) {
                    yield "JSONB";
                }
                yield switch (col.typeName()) {
                    case "int", "java.lang.Integer" -> "INTEGER";
                    case "long", "java.lang.Long" -> "BIGINT";
                    case "java.lang.String" -> "VARCHAR(255)";
                    case "java.time.LocalDate" -> "DATE";
                    case "java.time.LocalDateTime" -> "TIMESTAMP";
                    case "java.time.LocalTime" -> "TIME";
                    case "java.time.OffsetDateTime", "java.util.Date" -> "DATE";
                    case "org.jooq.JSONB" -> "JSONB";
                    case "java.lang.Boolean" -> "BOOLEAN";
                    case "org.jooq.JSON" -> "JSON";
                    default -> "VARCHAR(255)";
                };
            }
            default -> throw new IllegalArgumentException("Unsupported database type");
        };

        // 添加NOT NULL约束
        return baseType + (col.nullable() ? "" : " NOT NULL");
    }

    public String generate() {
        return String.join("\n\n", ddlStatements);
    }


    private boolean isEnumType(String typeName) {
        try {
            Class<?> clazz = Class.forName(typeName);
            return clazz.isEnum();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isCollectionOrMapType(String typeName) {
        return typeName.startsWith("java.util.Map")
                || typeName.startsWith("java.util.List")
                || typeName.startsWith("java.util.Set");
    }
}
