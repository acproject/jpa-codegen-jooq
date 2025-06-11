package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import org.jooq.DSLContext;

/**
 * 向量数据操作接口
 * 基于PGVector插件提供PostgreSQL向量数据库操作支持
 * 参考: https://help.aliyun.com/zh/polardb/polardb-for-postgresql/pgvector
 * 
 * @author acproject@qq.com
 * @date 2025-01-18
 */
public interface VectorDataOperation {
    
    // =============== 向量表和数据管理 ===============
    
    /**
     * 创建向量表
     * 支持指定向量维度和其他列
     */
    DataRecord createVectorTable(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 插入向量数据
     * 支持单条和批量插入
     */
    DataRecord insertVectorData(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 批量插入向量数据
     */
    DataRecord insertBatchVectorData(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 向量索引管理 ===============
    
    /**
     * 创建向量索引
     * 支持IVFFlat和HNSW索引类型
     */
    DataRecord createVectorIndex(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 删除向量索引
     */
    DataRecord dropVectorIndex(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 向量相似度查询 ===============
    
    /**
     * 欧几里得距离查询 (L2距离)
     * 使用 <-> 操作符
     */
    DataRecord vectorL2Search(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 余弦距离查询
     * 使用 <=> 操作符
     */
    DataRecord vectorCosineSearch(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 内积查询 (点积)
     * 使用 <#> 操作符
     */
    DataRecord vectorInnerProductSearch(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 向量聚合和分析 ===============
    
    /**
     * 向量聚合操作
     * 如计算平均向量、向量统计等
     */
    DataRecord vectorAggregation(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 向量维度分析
     * 获取向量维度信息和统计
     */
    DataRecord vectorDimensionAnalysis(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 向量工具函数 ===============
    
    /**
     * 向量归一化
     */
    DataRecord normalizeVector(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 向量运算
     * 支持向量加法、减法、数量乘法等
     */
    DataRecord vectorMath(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 向量类型转换
     * 在不同向量格式间转换
     */
    DataRecord convertVectorType(DSLContext dslContext, JsonNode rootNode);
} 