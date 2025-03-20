package com.owiseman.jpa.util;

import com.owiseman.jpa.model.ColumnMeta;
import com.owiseman.jpa.model.DataSourceEnum;
import com.owiseman.jpa.model.TableMeta;
import org.jooq.impl.SQLDataType;

import java.util.ArrayList;
import java.util.List;

import static com.owiseman.jpa.JpaEntityScannerProcessor.convertClassNameToTableName;

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
                sb.append("CREATE INDEX ").append("IF NOT EXISTS ").append(idx.name()).append(" ON ")
                        .append(table.name()).append(" (").append(String.join(", ", idx.columns())).append(");\n")
        );

        table.foreignKeys().forEach(fk ->
                sb.append("ALTER TABLE ").append(table.name())
                        .append(" ADD CONSTRAINT ").append(fk.name())
                        .append(" FOREIGN KEY (").append(fk.column())
                        .append(") REFERENCES ").append(fk.refTable())
                        .append(" (").append(fk.refColumn()).append(");\n")
        );

        // 新增外键约束生成（自动处理一对多关系）
        table.columns().stream()
                .filter(col -> col.typeName().contains("List<") || col.typeName().contains("Set<"))
                .forEach(col -> {
                    String fullTypeName = col.typeName().replaceAll(".*<([^>]+)>.*", "$1");
                    String simpleClassName = fullTypeName.replaceAll(".*\\.", "");
                    String refTable = convertClassNameToTableName(simpleClassName);

                    sb.append("ALTER TABLE ").append(table.name())
                            .append(" ADD FOREIGN KEY (").append(col.name())
                            .append(") REFERENCES ").append(refTable)
                            .append("(id);\n");
                });

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
                    case "float", "java.lang.Float" -> "FLOAT";        // 新增 float 类型
                    case "double", "java.lang.Double" -> "DOUBLE PRECISION"; // 新增 double 类型
                    case "java.util.UUID" -> "VARCHAR(255)";
                    case "java.lang.String" -> {
                        int length = col.length();
                        yield "VARCHAR(" + (length > 0 ? length : 255) + ")";
                    }
                    case "java.time.LocalDate" -> "DATE";
                    case "java.time.LocalDateTime" -> "TIMESTAMP";
                    case "java.time.LocalTime" -> "TIME";
                    case "java.time.OffsetDateTime", "java.util.Date" -> "DATE";
                    case "org.jooq.JSONB" -> "JSONB";
                    case "java.lang.Boolean" -> "BOOLEAN";
                    case "org.jooq.JSON" -> "JSON";
                    case "java.math.BigDecimal" -> "DECIMAL";
                    case "Numeric" -> "NUMERIC";
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
        // 排除包含关联关系的类型
        return (typeName.startsWith("java.util.Map") ||
                typeName.startsWith("java.util.List") ||
                typeName.startsWith("java.util.Set")) &&
                // 添加排除条件：如果泛型参数是实体类则不视为普通集合
                !typeName.matches(".*<.*[A-Z][a-zA-Z]+>.*");
    }
}
