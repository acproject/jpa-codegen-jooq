package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import org.jooq.DSLContext;

/**
 * JSON/JSONB数据类型操作接口
 * 支持PostgreSQL的JSON和JSONB数据类型的各种操作
 * 
 * @author acproject@qq.com
 * @date 2025-01-18
 */
public interface JsonDataOperation {
    
    // =============== JSON/JSONB 查询操作 ===============
    
    /**
     * 使用JSON路径表达式查询数据
     * 支持 ->, ->>, #>, #>> 等操作符
     */
    DataRecord queryJsonPath(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * JSON包含查询操作
     * 支持 @>, <@ 等操作符
     */
    DataRecord queryJsonContains(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * JSON键存在查询操作
     * 支持 ?, ?|, ?& 等操作符
     */
    DataRecord queryJsonKeys(DSLContext dslContext, JsonNode rootNode);
    
    // =============== JSON/JSONB 更新操作 ===============
    
    /**
     * 更新JSON字段的特定路径
     */
    DataRecord updateJsonPath(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 在JSON对象中添加或删除键值对
     */
    DataRecord modifyJsonKeys(DSLContext dslContext, JsonNode rootNode);
    
    // =============== JSON/JSONB 函数操作 ===============
    
    /**
     * 调用JSON处理函数
     * 支持 json_array_length, json_object_keys, json_typeof 等
     */
    DataRecord executeJsonFunction(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * JSON数据类型转换
     * 支持 to_json, array_to_json, row_to_json 等
     */
    DataRecord convertToJson(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * JSON数据解析
     * 支持 json_populate_record, json_to_record 等
     */
    DataRecord parseJsonData(DSLContext dslContext, JsonNode rootNode);
    
    // =============== JSON/JSONB 聚合操作 ===============
    
    /**
     * JSON数组聚合查询
     * 支持 json_array_elements, json_array_elements_text 等
     */
    DataRecord aggregateJsonArray(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * JSON对象聚合查询
     */
    DataRecord aggregateJsonObject(DSLContext dslContext, JsonNode rootNode);
} 