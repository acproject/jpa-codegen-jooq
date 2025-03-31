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

    private boolean isTableExists(String tableName) {
        return ddlStatements.stream().anyMatch(s -> s.contains("CREATE TABLE " + tableName));
    }

    private String buildCreateTableStatement(TableMeta table, DataSourceEnum dataSourceEnum) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(table.name()).append(" (\n");

        // 处理字段定义
        table.columns().forEach(col -> {
            // 跳过多对多关系中错误添加的集合字段（只针对多对多关系的集合字段）
            if (isCollectionOrMapType(col.typeName()) &&
                    (col.typeName().contains("List<") || col.typeName().contains("Set<")) &&
                    !col.name().endsWith("_id")) { // 保留真正的外键字段
                return; // 跳过这个字段
            }

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

        // 处理外键约束 - 只处理明确定义的外键
        table.foreignKeys().forEach(fk -> {
                    sb.append("ALTER TABLE ").append(table.name())
                            .append(" ADD CONSTRAINT ").append(fk.name())
                            .append(" FOREIGN KEY (").append(fk.column())
                            .append(") REFERENCES ").append(fk.refTable())
                            .append(" (").append(fk.refColumn()).append(");\n");
                }
        );

        // 移除自动为_id字段创建外键的逻辑
        // 下面这段代码被注释掉或删除
        /*
        table.columns().stream()
                .filter(col -> {
                    // 只处理真正的外键字段，不处理集合字段
                    return col.name().endsWith("_id") && !isCollectionOrMapType(col.typeName());
                })
                .forEach(col -> {
                    // 只有当这个外键没有被明确定义时才添加
                    if (table.foreignKeys().stream().noneMatch(fk -> fk.column().equals(col.name()))) {
                        String refTable = col.name().substring(0, col.name().length() - 3); // 移除"_id"
                        sb.append("ALTER TABLE ").append(table.name())
                                .append(" ADD CONSTRAINT fk_").append(table.name()).append("_").append(col.name())
                                .append(" FOREIGN KEY (").append(col.name())
                                .append(") REFERENCES ").append(refTable)
                                .append("(id);\n");
                    }
                });
        */

        return sb.toString();
    }


    @Override
    public String mapToType(ColumnMeta col, DataSourceEnum dataSourceEnum) {
        // 首先检查是否有columnDefinition属性（我们需要在ColumnMeta中添加这个属性）
        if (col.hasColumnDefinition()) {
            String columnDef = col.columnDefinition().toUpperCase();
            if (columnDef.contains("JSON") && !columnDef.contains("JSONB")) {
                return "JSON" + (col.nullable() ? "" : " NOT NULL");
            } else if (columnDef.contains("JSONB")) {
                return "JSONB" + (col.nullable() ? "" : " NOT NULL");
            } else if (columnDef.contains("TEXT")) {
                return "TEXT" + (col.nullable() ? "" : " NOT NULL");
            }
            // 对于其他自定义类型，直接使用columnDefinition的值
            return columnDef + (col.nullable() ? "" : " NOT NULL");
        }

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
                        if (col.hasColumnDefinition()) {
                            String def = col.columnDefinition().toUpperCase();
                            if (def.contains("TEXT")) {
                                yield "TEXT";
                            }
                        }
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
