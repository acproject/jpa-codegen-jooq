package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import lombok.extern.log4j.Log4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.util.*;

/**
 * JSON/JSONB数据类型操作实现类
 * 提供对PostgreSQL JSON和JSONB数据类型的完整操作支持
 * 
 * @author acproject@qq.com
 * @date 2025-01-18
 */
@Log4j
public class JsonDataUtil implements JsonDataOperation {
    
    private static volatile JsonDataUtil instance;
    
    private JsonDataUtil() {
    }
    
    public static JsonDataUtil getInstance() {
        if (instance == null) {
            synchronized (JsonDataUtil.class) {
                if (instance == null) {
                    instance = new JsonDataUtil();
                }
            }
        }
        return instance;
    }
    
    @Override
    public DataRecord queryJsonPath(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String operation = rootNode.get("json_operation").asText();
        JsonNode pathNode = rootNode.get("path");
        
        // 构建JSON路径表达式
        Field<?> jsonField = buildJsonPathExpression(jsonColumn, operation, pathNode);
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .select(DSL.field("*"), jsonField.as("json_result"))
                .from(DSL.table(tableName))
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON path query executed: " + operation + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_path_query", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord queryJsonContains(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String operation = rootNode.get("contains_operation").asText();
        String jsonValue = rootNode.get("json_value").asText();
        
        // 构建包含条件
        Condition condition = buildJsonContainsCondition(jsonColumn, operation, jsonValue);
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .from(DSL.table(tableName))
                .where(condition)
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON contains query executed: " + operation + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_contains_query", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord queryJsonKeys(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String operation = rootNode.get("key_operation").asText();
        JsonNode keysNode = rootNode.get("keys");
        
        // 构建键存在条件
        Condition condition = buildJsonKeysCondition(jsonColumn, operation, keysNode);
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .from(DSL.table(tableName))
                .where(condition)
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON keys query executed: " + operation + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_keys_query", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord updateJsonPath(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        JsonNode pathNode = rootNode.get("path");
        String newValue = rootNode.get("new_value").asText();
        JsonNode whereNode = rootNode.get("where");
        
        // 使用原生SQL进行JSONB更新
        String pathStr = buildJsonPathString(pathNode);
        String sql = String.format("UPDATE %s SET %s = jsonb_set(%s, '%s', '\"%s\"') WHERE %s", 
                tableName, jsonColumn, jsonColumn, pathStr, newValue, buildWhereClause(whereNode));
        
        int updated = dslContext.execute(sql);
        
        log.info("Updated " + updated + " rows with JSON path update on " + tableName + "." + jsonColumn);
        return new DataRecord("json_path_update", tableName + " (" + updated + " rows)", 
                Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord modifyJsonKeys(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String operation = rootNode.get("modify_operation").asText();
        JsonNode modificationsNode = rootNode.get("modifications");
        JsonNode whereNode = rootNode.get("where");
        
        String sql = "";
        if ("add".equalsIgnoreCase(operation)) {
            String jsonToAdd = modificationsNode.toString();
            sql = String.format("UPDATE %s SET %s = %s || '%s' WHERE %s",
                    tableName, jsonColumn, jsonColumn, jsonToAdd, buildWhereClause(whereNode));
        } else if ("remove".equalsIgnoreCase(operation)) {
            if (modificationsNode.isArray()) {
                for (JsonNode keyNode : modificationsNode) {
                    String key = keyNode.asText();
                    sql = String.format("UPDATE %s SET %s = %s - '%s' WHERE %s",
                            tableName, jsonColumn, jsonColumn, key, buildWhereClause(whereNode));
                    dslContext.execute(sql);
                }
                return new DataRecord("json_keys_modify", tableName, Optional.empty(), Optional.empty());
            } else {
                String key = modificationsNode.asText();
                sql = String.format("UPDATE %s SET %s = %s - '%s' WHERE %s",
                        tableName, jsonColumn, jsonColumn, key, buildWhereClause(whereNode));
            }
        }
        
        int updated = dslContext.execute(sql);
        
        log.info("Modified " + updated + " rows with JSON keys " + operation + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_keys_modify", tableName + " (" + updated + " rows)", 
                Optional.empty(), Optional.empty());
    }
    
    @Override
    public DataRecord executeJsonFunction(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String functionName = rootNode.get("function").asText();
        JsonNode parametersNode = rootNode.get("parameters");
        
        // 构建函数调用
        Field<?> functionField = buildJsonFunction(functionName, jsonColumn, parametersNode);
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .select(DSL.field("*"), functionField.as("function_result"))
                .from(DSL.table(tableName))
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON function executed: " + functionName + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_function", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord convertToJson(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String sourceColumn = rootNode.get("source_column").asText();
        String convertFunction = rootNode.get("convert_function").asText();
        
        // 构建转换函数
        Field<?> convertField = switch (convertFunction) {
            case "to_json" -> DSL.function("to_json", Object.class, DSL.field(sourceColumn));
            case "array_to_json" -> DSL.function("array_to_json", Object.class, DSL.field(sourceColumn));
            case "row_to_json" -> DSL.function("row_to_json", Object.class, DSL.field(sourceColumn));
            default -> throw new IllegalArgumentException("Unsupported convert function: " + convertFunction);
        };
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .select(DSL.field("*"), convertField.as("json_result"))
                .from(DSL.table(tableName))
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON conversion executed: " + convertFunction + " on " + tableName + "." + sourceColumn);
        return new DataRecord("json_convert", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord parseJsonData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String parseFunction = rootNode.get("parse_function").asText();
        
        // 使用原生SQL执行解析函数
        String sql = switch (parseFunction) {
            case "json_populate_record" -> {
                String targetType = rootNode.get("target_type").asText();
                yield String.format("SELECT *, json_populate_record(null::%s, %s) as parsed_result FROM %s",
                        targetType, jsonColumn, tableName);
            }
            case "json_to_record" -> String.format("SELECT *, json_to_record(%s) as parsed_result FROM %s",
                    jsonColumn, tableName);
            case "json_to_recordset" -> String.format("SELECT * FROM json_to_recordset((SELECT array_agg(%s) FROM %s)) as x",
                    jsonColumn, tableName);
            default -> throw new IllegalArgumentException("Unsupported parse function: " + parseFunction);
        };
        
        Result<Record> result = dslContext.fetch(sql);
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON parsing executed: " + parseFunction + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_parse", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord aggregateJsonArray(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String aggregateFunction = rootNode.get("aggregate_function").asText();
        
        // 构建聚合函数
        Field<?> aggregateField = switch (aggregateFunction) {
            case "json_array_elements" -> DSL.function("json_array_elements", Object.class, DSL.field(jsonColumn));
            case "json_array_elements_text" -> DSL.function("json_array_elements_text", Object.class, DSL.field(jsonColumn));
            case "json_array_length" -> DSL.function("json_array_length", Object.class, DSL.field(jsonColumn));
            default -> throw new IllegalArgumentException("Unsupported aggregate function: " + aggregateFunction);
        };
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .select(aggregateField.as("aggregate_result"))
                .from(DSL.table(tableName))
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON array aggregation executed: " + aggregateFunction + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_array_aggregate", tableName, Optional.of(resultList), Optional.empty());
    }
    
    @Override
    public DataRecord aggregateJsonObject(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String jsonColumn = rootNode.get("json_column").asText();
        String aggregateFunction = rootNode.get("aggregate_function").asText();
        
        // 构建聚合函数
        Field<?> aggregateField = switch (aggregateFunction) {
            case "json_object_keys" -> DSL.function("json_object_keys", Object.class, DSL.field(jsonColumn));
            case "json_typeof" -> DSL.function("json_typeof", Object.class, DSL.field(jsonColumn));
            default -> throw new IllegalArgumentException("Unsupported aggregate function: " + aggregateFunction);
        };
        
        // 执行查询
        Result<Record> result = dslContext.select()
                .select(aggregateField.as("aggregate_result"))
                .from(DSL.table(tableName))
                .fetch();
        
        List<Map<String, Object>> resultList = TableAndDataUtil.getInstance().convertToMapList(result);
        
        log.info("JSON object aggregation executed: " + aggregateFunction + " on " + tableName + "." + jsonColumn);
        return new DataRecord("json_object_aggregate", tableName, Optional.of(resultList), Optional.empty());
    }
    
    // =============== 辅助方法 ===============
    
    private Field<?> buildJsonPathExpression(String jsonColumn, String operation, JsonNode pathNode) {
        return switch (operation) {
            case "->" -> {
                if (pathNode.isInt()) {
                    // 数组索引访问 
                    yield DSL.field(DSL.name(jsonColumn + "->" + pathNode.asInt()));
                } else {
                    // 对象键访问
                    yield DSL.field(DSL.name(jsonColumn + "->'" + pathNode.asText() + "'"));
                }
            }
            case "->>" -> {
                if (pathNode.isInt()) {
                    yield DSL.field(DSL.name(jsonColumn + "->>" + pathNode.asInt()));
                } else {
                    yield DSL.field(DSL.name(jsonColumn + "->>'" + pathNode.asText() + "'"));
                }
            }
            case "#>" -> {
                String pathStr = buildJsonPathString(pathNode);
                yield DSL.field(DSL.name(jsonColumn + "#>" + pathStr));
            }
            case "#>>" -> {
                String pathStr = buildJsonPathString(pathNode);
                yield DSL.field(DSL.name(jsonColumn + "#>>" + pathStr));
            }
            default -> throw new IllegalArgumentException("Unsupported JSON operation: " + operation);
        };
    }
    
    private String buildJsonPathString(JsonNode pathNode) {
        if (pathNode.isArray()) {
            List<String> paths = new ArrayList<>();
            pathNode.forEach(node -> paths.add(node.asText()));
            return "'{" + String.join(",", paths) + "}'";
        } else {
            return "'{" + pathNode.asText() + "}'";
        }
    }
    
    private Condition buildJsonContainsCondition(String jsonColumn, String operation, String jsonValue) {
        return switch (operation) {
            case "@>" -> DSL.condition(jsonColumn + " @> '" + jsonValue + "'");
            case "<@" -> DSL.condition(jsonColumn + " <@ '" + jsonValue + "'");
            default -> throw new IllegalArgumentException("Unsupported contains operation: " + operation);
        };
    }
    
    private Condition buildJsonKeysCondition(String jsonColumn, String operation, JsonNode keysNode) {
        return switch (operation) {
            case "?" -> {
                String key = keysNode.asText();
                yield DSL.condition(jsonColumn + " ? '" + key + "'");
            }
            case "?|" -> {
                List<String> keys = new ArrayList<>();
                keysNode.forEach(node -> keys.add("'" + node.asText() + "'"));
                String keysArray = "ARRAY[" + String.join(",", keys) + "]";
                yield DSL.condition(jsonColumn + " ?| " + keysArray);
            }
            case "?&" -> {
                List<String> keys = new ArrayList<>();
                keysNode.forEach(node -> keys.add("'" + node.asText() + "'"));
                String keysArray = "ARRAY[" + String.join(",", keys) + "]";
                yield DSL.condition(jsonColumn + " ?& " + keysArray);
            }
            default -> throw new IllegalArgumentException("Unsupported keys operation: " + operation);
        };
    }
    
    private Field<?> buildJsonFunction(String functionName, String jsonColumn, JsonNode parametersNode) {
        return switch (functionName) {
            case "json_array_length" -> DSL.function("json_array_length", Object.class, DSL.field(jsonColumn));
            case "json_object_keys" -> DSL.function("json_object_keys", Object.class, DSL.field(jsonColumn));
            case "json_typeof" -> DSL.function("json_typeof", Object.class, DSL.field(jsonColumn));
            case "json_extract_path" -> {
                List<Field<?>> params = new ArrayList<>();
                params.add(DSL.field(jsonColumn));
                if (parametersNode != null && parametersNode.isArray()) {
                    parametersNode.forEach(param -> params.add(DSL.val(param.asText())));
                }
                yield DSL.function("json_extract_path", Object.class, params.toArray(new Field[0]));
            }
            default -> throw new IllegalArgumentException("Unsupported JSON function: " + functionName);
        };
    }
    
    private String buildWhereClause(JsonNode whereNode) {
        if (whereNode == null) {
            return "1=1";
        }
        
        List<String> conditions = new ArrayList<>();
        whereNode.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            String value = entry.getValue().asText();
            conditions.add(fieldName + " = '" + value + "'");
        });
        
        return conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);
    }
}
