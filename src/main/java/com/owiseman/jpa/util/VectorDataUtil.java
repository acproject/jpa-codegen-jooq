package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import com.owiseman.jpa.model.PaginationInfo;
import lombok.extern.log4j.Log4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 向量数据操作实现类
 * 基于PGVector插件提供PostgreSQL向量数据库操作支持
 * 支持IVFFlat和HNSW索引，最高16000维度向量
 * 
 * @author acproject@qq.com
 * @date 2025-01-18
 */
@Log4j
public class VectorDataUtil implements VectorDataOperation {
    
    private static volatile VectorDataUtil instance;
    
    private VectorDataUtil() {
    }
    
    public static VectorDataUtil getInstance() {
        if (instance == null) {
            synchronized (VectorDataUtil.class) {
                if (instance == null) {
                    instance = new VectorDataUtil();
                }
            }
        }
        return instance;
    }
    
    @Override
    public DataRecord createVectorTable(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") && 
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(createVectorTableLogic(configuration.dsl(), rootNode));
            });
        } else {
            result.set(createVectorTableLogic(dslContext, rootNode));
        }
        return result.get();
    }
    
    private DataRecord createVectorTableLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        int vectorDimension = rootNode.get("vector_dimension").asInt();
        String vectorColumn = rootNode.get("vector_column").asText();
        JsonNode additionalColumns = rootNode.get("additional_columns");
        
        // 验证向量维度
        if (vectorDimension <= 0 || vectorDimension > 16000) {
            throw new IllegalArgumentException("Vector dimension must be between 1 and 16000");
        }
        
        // 确保vector扩展已启用
        dslContext.execute("CREATE EXTENSION IF NOT EXISTS vector");
        
        // 使用原生SQL创建向量表
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        
        List<String> columns = new ArrayList<>();
        
        // 添加主键列（如果指定）
        if (rootNode.has("id_column")) {
            String idColumn = rootNode.get("id_column").asText();
            columns.add(idColumn + " SERIAL PRIMARY KEY");
        }
        
        // 添加向量列
        columns.add(vectorColumn + " vector(" + vectorDimension + ")");
        
        // 添加其他列
        if (additionalColumns != null && additionalColumns.isArray()) {
            for (JsonNode column : additionalColumns) {
                String columnName = column.get("name").asText();
                String columnType = column.get("type").asText();
                String columnDef = columnName + " " + columnType;
                
                if (column.has("nullable") && !column.get("nullable").asBoolean()) {
                    columnDef += " NOT NULL";
                }
                
                columns.add(columnDef);
            }
        }
        
        sql.append(String.join(", ", columns));
        sql.append(")");
        
        dslContext.execute(sql.toString());
        
        log.info("Created vector table: " + tableName + " with " + vectorDimension + " dimensions");
        return new DataRecord("create_vector_table", tableName, Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord insertVectorData(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") && 
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(insertVectorDataLogic(configuration.dsl(), rootNode));
            });
        } else {
            result.set(insertVectorDataLogic(dslContext, rootNode));
        }
        return result.get();
    }
    
    private DataRecord insertVectorDataLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        JsonNode vectorData = rootNode.get("vector_data");
        JsonNode additionalData = rootNode.get("additional_data");
        
        Map<String, Object> insertData = new LinkedHashMap<>();
        
        // 处理向量数据
        String vectorValue = formatVectorData(vectorData);
        insertData.put(vectorColumn, vectorValue);
        
        // 处理其他数据
        if (additionalData != null) {
            additionalData.fields().forEachRemaining(entry -> {
                insertData.put(entry.getKey(), entry.getValue().asText());
            });
        }
        
        dslContext.insertInto(DSL.table(tableName))
                .set(insertData)
                .execute();
        
        log.info("Inserted vector data into table: " + tableName);
        return new DataRecord("insert_vector_data", tableName, Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord insertBatchVectorData(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") && 
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(insertBatchVectorDataLogic(configuration.dsl(), rootNode));
            });
        } else {
            result.set(insertBatchVectorDataLogic(dslContext, rootNode));
        }
        return result.get();
    }
    
    private DataRecord insertBatchVectorDataLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        JsonNode batchData = rootNode.get("batch_data");
        
        if (!batchData.isArray()) {
            throw new IllegalArgumentException("batch_data must be an array");
        }
        
        // 使用原生SQL进行批量插入以处理向量类型
        for (JsonNode dataItem : batchData) {
            JsonNode vectorData = dataItem.get("vector_data");
            String vectorValue = formatVectorData(vectorData);
            
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ").append(tableName).append(" (").append(vectorColumn);
            
            List<String> values = new ArrayList<>();
            values.add("'" + vectorValue + "'");
            
            // 处理其他数据
            List<String> otherColumns = new ArrayList<>();
            dataItem.fields().forEachRemaining(entry -> {
                if (!"vector_data".equals(entry.getKey())) {
                    otherColumns.add(entry.getKey());
                    values.add("'" + entry.getValue().asText() + "'");
                }
            });
            
            if (!otherColumns.isEmpty()) {
                sql.append(", ").append(String.join(", ", otherColumns));
            }
            
            sql.append(") VALUES (").append(String.join(", ", values)).append(")");
            
            dslContext.execute(sql.toString());
        }
        
        log.info("Inserted " + batchData.size() + " vector records into table: " + tableName);
        return new DataRecord("insert_batch_vector_data", tableName + " (" + batchData.size() + " rows)", 
                Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord createVectorIndex(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") && 
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(createVectorIndexLogic(configuration.dsl(), rootNode));
            });
        } else {
            result.set(createVectorIndexLogic(dslContext, rootNode));
        }
        return result.get();
    }
    
    private DataRecord createVectorIndexLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        String indexName = rootNode.get("index_name").asText();
        String indexType = rootNode.get("index_type").asText(); // ivfflat 或 hnsw
        String operatorClass = rootNode.get("operator_class").asText(); // vector_l2_ops, vector_ip_ops, vector_cosine_ops
        JsonNode indexParams = rootNode.get("index_params");
        
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE INDEX ").append(indexName)
           .append(" ON ").append(tableName)
           .append(" USING ").append(indexType.toLowerCase())
           .append("(").append(vectorColumn).append(" ").append(operatorClass).append(")");
        
        // 添加索引参数
        if (indexParams != null) {
            List<String> params = new ArrayList<>();
            indexParams.fields().forEachRemaining(entry -> {
                params.add(entry.getKey() + " = " + entry.getValue().asText());
            });
            
            if (!params.isEmpty()) {
                sql.append(" WITH (").append(String.join(", ", params)).append(")");
            }
        }
        
        dslContext.execute(sql.toString());
        
        log.info("Created " + indexType + " vector index: " + indexName + " on " + tableName + "." + vectorColumn);
        return new DataRecord("create_vector_index", indexName, Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord dropVectorIndex(DSLContext dslContext, JsonNode rootNode) {
        String indexName = rootNode.get("index_name").asText();
        boolean ifExists = rootNode.has("if_exists") && rootNode.get("if_exists").asBoolean();
        
        String sql = "DROP INDEX " + (ifExists ? "IF EXISTS " : "") + indexName;
        dslContext.execute(sql);
        
        log.info("Dropped vector index: " + indexName);
        return new DataRecord("drop_vector_index", indexName, Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord vectorL2Search(DSLContext dslContext, JsonNode rootNode) {
        return executeVectorSearch(dslContext, rootNode, "<->", "L2 distance");
    }
    
    @Override
    public DataRecord vectorCosineSearch(DSLContext dslContext, JsonNode rootNode) {
        return executeVectorSearch(dslContext, rootNode, "<=>", "cosine distance");
    }
    
    @Override
    public DataRecord vectorInnerProductSearch(DSLContext dslContext, JsonNode rootNode) {
        return executeVectorSearch(dslContext, rootNode, "<#>", "inner product");
    }
    
    private DataRecord executeVectorSearch(DSLContext dslContext, JsonNode rootNode, 
                                         String operator, String operatorName) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        JsonNode queryVector = rootNode.get("query_vector");
        int limit = rootNode.has("limit") ? rootNode.get("limit").asInt() : 10;
        JsonNode whereNode = rootNode.get("where");
        JsonNode paginationNode = rootNode.get("pagination");
        
        String queryVectorStr = formatVectorData(queryVector);
        
        // 构建查询
        String distanceField = vectorColumn + " " + operator + " '" + queryVectorStr + "'";
        
        String sql = "SELECT *, " + distanceField + " AS distance FROM " + tableName;
        
        // 添加WHERE条件
        if (whereNode != null) {
            List<String> conditions = new ArrayList<>();
            whereNode.fields().forEachRemaining(entry -> {
                conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
            });
            if (!conditions.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", conditions);
            }
        }
        
        sql += " ORDER BY " + distanceField;
        
        // 处理分页
        PaginationInfo paginationInfo = null;
        if (paginationNode != null) {
            int page = paginationNode.get("page").asInt();
            int pageSize = paginationNode.get("pageSize").asInt();
            int offset = (page - 1) * pageSize;
            sql += " LIMIT " + pageSize + " OFFSET " + offset;
            
            // 计算总数（不包含ORDER BY的查询）
            String countSql = "SELECT COUNT(*) FROM " + tableName;
            if (whereNode != null) {
                List<String> conditions = new ArrayList<>();
                whereNode.fields().forEachRemaining(entry -> {
                    conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
                });
                if (!conditions.isEmpty()) {
                    countSql += " WHERE " + String.join(" AND ", conditions);
                }
            }
            
            long total = dslContext.fetchOne(countSql).into(Long.class);
            paginationInfo = new PaginationInfo(page, pageSize, total, 
                Math.toIntExact((total + pageSize - 1) / pageSize));
        } else {
            sql += " LIMIT " + limit;
        }
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector " + operatorName + " search executed on " + tableName + "." + vectorColumn);
        return new DataRecord("vector_search", tableName, Optional.of(resultList), Optional.ofNullable(paginationInfo));
    }
    
    @Override
    public DataRecord vectorAggregation(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        String aggregationType = rootNode.get("aggregation_type").asText();
        JsonNode whereNode = rootNode.get("where");
        
        String sql = switch (aggregationType.toLowerCase()) {
            case "avg" -> "SELECT avg(" + vectorColumn + ") as avg_vector FROM " + tableName;
            case "count" -> "SELECT COUNT(*) as vector_count FROM " + tableName;
            case "dimension" -> "SELECT array_length(" + vectorColumn + ", 1) as dimension FROM " + tableName + " LIMIT 1";
            default -> throw new IllegalArgumentException("Unsupported aggregation type: " + aggregationType);
        };
        
        // 添加WHERE条件
        if (whereNode != null && !aggregationType.equals("dimension")) {
            List<String> conditions = new ArrayList<>();
            whereNode.fields().forEachRemaining(entry -> {
                conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
            });
            if (!conditions.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", conditions);
            }
        }
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector aggregation (" + aggregationType + ") executed on " + tableName + "." + vectorColumn);
        return new DataRecord("vector_aggregation", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord vectorDimensionAnalysis(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        
        String sql = String.format("""
            SELECT 
                COUNT(*) as total_vectors,
                array_length(%s, 1) as dimension
            FROM %s 
            WHERE %s IS NOT NULL
            LIMIT 1
            """, vectorColumn, tableName, vectorColumn);
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector dimension analysis executed on " + tableName + "." + vectorColumn);
        return new DataRecord("vector_dimension_analysis", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord normalizeVector(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        JsonNode whereNode = rootNode.get("where");
        
        // PostgreSQL向量归一化
        String sql = "SELECT *, " + vectorColumn + " / sqrt(" + vectorColumn + " <#> " + vectorColumn + ") as normalized_vector FROM " + tableName;
        
        // 添加WHERE条件
        if (whereNode != null) {
            List<String> conditions = new ArrayList<>();
            whereNode.fields().forEachRemaining(entry -> {
                conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
            });
            if (!conditions.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", conditions);
            }
        }
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector normalization executed on " + tableName + "." + vectorColumn);
        return new DataRecord("normalize_vector", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord vectorMath(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        String operation = rootNode.get("operation").asText();
        JsonNode operandVector = rootNode.get("operand_vector");
        Double scalar = rootNode.has("scalar") ? rootNode.get("scalar").asDouble() : null;
        JsonNode whereNode = rootNode.get("where");
        
        String operandVectorStr = operandVector != null ? formatVectorData(operandVector) : null;
        
        String mathExpression = switch (operation.toLowerCase()) {
            case "add" -> vectorColumn + " + '" + operandVectorStr + "'";
            case "subtract" -> vectorColumn + " - '" + operandVectorStr + "'";
            case "multiply" -> vectorColumn + " * " + scalar;
            case "divide" -> vectorColumn + " / " + scalar;
            default -> throw new IllegalArgumentException("Unsupported vector operation: " + operation);
        };
        
        String sql = "SELECT *, " + mathExpression + " as result_vector FROM " + tableName;
        
        // 添加WHERE条件
        if (whereNode != null) {
            List<String> conditions = new ArrayList<>();
            whereNode.fields().forEachRemaining(entry -> {
                conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
            });
            if (!conditions.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", conditions);
            }
        }
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector math operation (" + operation + ") executed on " + tableName + "." + vectorColumn);
        return new DataRecord("vector_math", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord convertVectorType(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String vectorColumn = rootNode.get("vector_column").asText();
        String targetFormat = rootNode.get("target_format").asText();
        JsonNode whereNode = rootNode.get("where");
        
        String conversionExpression = switch (targetFormat.toLowerCase()) {
            case "array" -> vectorColumn + "::float[]";
            case "text" -> vectorColumn + "::text";
            case "json" -> "to_json(" + vectorColumn + "::float[])";
            default -> throw new IllegalArgumentException("Unsupported target format: " + targetFormat);
        };
        
        String sql = "SELECT *, " + conversionExpression + " as converted_vector FROM " + tableName;
        
        // 添加WHERE条件
        if (whereNode != null) {
            List<String> conditions = new ArrayList<>();
            whereNode.fields().forEachRemaining(entry -> {
                conditions.add(entry.getKey() + " = '" + entry.getValue().asText() + "'");
            });
            if (!conditions.isEmpty()) {
                sql += " WHERE " + String.join(" AND ", conditions);
            }
        }
        
        Result<org.jooq.Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("Vector type conversion to " + targetFormat + " executed on " + tableName + "." + vectorColumn);
        return new DataRecord("convert_vector_type", tableName, Optional.of(resultList), Optional.empty());
    }
    
    // =============== 辅助方法 ===============
    
    private String formatVectorData(JsonNode vectorData) {
        if (vectorData.isArray()) {
            List<String> values = new ArrayList<>();
            vectorData.forEach(value -> values.add(value.asText()));
            return "[" + String.join(",", values) + "]";
        } else if (vectorData.isTextual()) {
            return vectorData.asText();
        } else {
            throw new IllegalArgumentException("Vector data must be an array or string");
        }
    }
}
