# PostgreSQL向量数据库操作示例

基于PGVector插件的向量数据库操作，支持高维向量存储、索引和相似度搜索。

## 目录
1. [向量表管理](#向量表管理)
2. [向量数据插入](#向量数据插入)
3. [向量索引管理](#向量索引管理)
4. [向量相似度搜索](#向量相似度搜索)
5. [向量聚合和分析](#向量聚合和分析)
6. [向量工具函数](#向量工具函数)
7. [实际应用场景](#实际应用场景)
8. [性能优化建议](#性能优化建议)

## 向量表管理

### 1. 创建向量表

创建支持512维向量的文档表：

```json
{
  "operation": "create_vector_table",
  "table": "documents",
  "vector_dimension": 512,
  "vector_column": "embedding",
  "id_column": "id",
  "additional_columns": [
    {
      "name": "title",
      "type": "VARCHAR(255)",
      "nullable": false
    },
    {
      "name": "content",
      "type": "TEXT"
    },
    {
      "name": "category",
      "type": "VARCHAR(100)"
    },
    {
      "name": "created_at",
      "type": "TIMESTAMP DEFAULT NOW()"
    }
  ],
  "use_transaction": true
}
```

创建用于图像特征的向量表：

```json
{
  "operation": "create_vector_table",
  "table": "images",
  "vector_dimension": 1536,
  "vector_column": "feature_vector",
  "id_column": "image_id",
  "additional_columns": [
    {
      "name": "filename",
      "type": "VARCHAR(255)",
      "nullable": false
    },
    {
      "name": "file_path",
      "type": "VARCHAR(500)"
    },
    {
      "name": "image_type",
      "type": "VARCHAR(50)"
    },
    {
      "name": "upload_time",
      "type": "TIMESTAMP DEFAULT NOW()"
    }
  ],
  "use_transaction": true
}
```

## 向量数据插入

### 2. 插入单条向量数据

插入文档向量：

```json
{
  "operation": "insert_vector_data",
  "table": "documents",
  "vector_column": "embedding",
  "vector_data": [0.1, 0.2, 0.3, -0.1, 0.4, 0.0, 0.2, 0.1],
  "additional_data": {
    "title": "机器学习入门",
    "content": "这是一篇关于机器学习基础概念的文章...",
    "category": "技术"
  },
  "use_transaction": true
}
```

### 3. 批量插入向量数据

批量插入多个文档向量：

```json
{
  "operation": "insert_batch_vector_data",
  "table": "documents",
  "vector_column": "embedding",
  "batch_data": [
    {
      "vector_data": [0.1, 0.2, 0.3, -0.1, 0.4, 0.0, 0.2, 0.1],
      "title": "深度学习概述",
      "content": "深度学习是机器学习的一个子领域...",
      "category": "技术"
    },
    {
      "vector_data": [0.2, -0.1, 0.4, 0.1, 0.3, 0.5, -0.2, 0.0],
      "title": "自然语言处理",
      "content": "NLP是人工智能的重要分支...",
      "category": "AI"
    },
    {
      "vector_data": [0.3, 0.1, -0.2, 0.4, 0.0, 0.2, 0.3, 0.1],
      "title": "计算机视觉",
      "content": "计算机视觉让机器能够理解图像...",
      "category": "AI"
    }
  ],
  "use_transaction": true
}
```

## 向量索引管理

### 4. 创建IVFFlat索引

创建用于L2距离的IVFFlat索引：

```json
{
  "operation": "create_vector_index",
  "table": "documents",
  "vector_column": "embedding",
  "index_name": "idx_documents_embedding_ivf",
  "index_type": "ivfflat",
  "operator_class": "vector_l2_ops",
  "index_params": {
    "lists": "100"
  },
  "use_transaction": true
}
```

### 5. 创建HNSW索引

创建高性能HNSW索引：

```json
{
  "operation": "create_vector_index",
  "table": "documents",
  "vector_column": "embedding",
  "index_name": "idx_documents_embedding_hnsw",
  "index_type": "hnsw",
  "operator_class": "vector_cosine_ops",
  "index_params": {
    "m": "16",
    "ef_construction": "64"
  },
  "use_transaction": true
}
```

### 6. 删除向量索引

```json
{
  "operation": "drop_vector_index",
  "index_name": "idx_documents_embedding_ivf",
  "if_exists": true
}
```

## 向量相似度搜索

### 7. L2距离搜索（欧几里得距离）

搜索最相似的文档：

```json
{
  "operation": "vector_l2_search",
  "table": "documents",
  "vector_column": "embedding",
  "query_vector": [0.15, 0.25, 0.35, -0.05, 0.45, 0.05, 0.25, 0.15],
  "limit": 5,
  "where": {
    "category": "技术"
  },
  "pagination": {
    "page": 1,
    "pageSize": 10
  }
}
```

### 8. 余弦相似度搜索

```json
{
  "operation": "vector_cosine_search",
  "table": "documents",
  "vector_column": "embedding",
  "query_vector": [0.1, 0.2, 0.3, -0.1, 0.4, 0.0, 0.2, 0.1],
  "limit": 10,
  "where": {
    "category": "AI"
  }
}
```

### 9. 内积搜索（点积）

```json
{
  "operation": "vector_inner_product_search",
  "table": "images",
  "vector_column": "feature_vector",
  "query_vector": [0.2, -0.1, 0.4, 0.1, 0.3, 0.5, -0.2, 0.0],
  "limit": 20,
  "where": {
    "image_type": "jpg"
  }
}
```

## 向量聚合和分析

### 10. 向量聚合操作

计算平均向量：

```json
{
  "operation": "vector_aggregation",
  "table": "documents",
  "vector_column": "embedding",
  "aggregation_type": "avg",
  "where": {
    "category": "技术"
  }
}
```

统计向量数量：

```json
{
  "operation": "vector_aggregation",
  "table": "documents",
  "vector_column": "embedding",
  "aggregation_type": "count"
}
```

获取向量维度：

```json
{
  "operation": "vector_aggregation",
  "table": "documents",
  "vector_column": "embedding",
  "aggregation_type": "dimension"
}
```

### 11. 向量维度分析

```json
{
  "operation": "vector_dimension_analysis",
  "table": "documents",
  "vector_column": "embedding"
}
```

## 向量工具函数

### 12. 向量归一化

```json
{
  "operation": "normalize_vector",
  "table": "documents",
  "vector_column": "embedding",
  "where": {
    "category": "技术"
  }
}
```

### 13. 向量数学运算

向量加法：

```json
{
  "operation": "vector_math",
  "table": "documents",
  "vector_column": "embedding",
  "operation": "add",
  "operand_vector": [0.1, 0.0, -0.1, 0.2, 0.0, 0.1, -0.05, 0.05],
  "where": {
    "id": "1"
  }
}
```

向量数量乘法：

```json
{
  "operation": "vector_math",
  "table": "documents",
  "vector_column": "embedding",
  "operation": "multiply",
  "scalar": 2.5,
  "where": {
    "id": "1"
  }
}
```

### 14. 向量类型转换

转换为数组格式：

```json
{
  "operation": "convert_vector_type",
  "table": "documents",
  "vector_column": "embedding",
  "target_format": "array",
  "where": {
    "id": "1"
  }
}
```

转换为JSON格式：

```json
{
  "operation": "convert_vector_type",
  "table": "documents",
  "vector_column": "embedding",
  "target_format": "json"
}
```

## 实际应用场景

### 文档相似度搜索系统

```json
{
  "operation": "vector_cosine_search",
  "table": "knowledge_base",
  "vector_column": "content_embedding",
  "query_vector": "从用户查询生成的向量",
  "limit": 5,
  "where": {
    "status": "published",
    "language": "zh-CN"
  },
  "pagination": {
    "page": 1,
    "pageSize": 10
  }
}
```

### 图像搜索引擎

```json
{
  "operation": "vector_l2_search",
  "table": "image_features",
  "vector_column": "visual_embedding",
  "query_vector": "从上传图像提取的特征向量",
  "limit": 20,
  "where": {
    "resolution": "high",
    "format": "RGB"
  }
}
```

### 推荐系统

```json
{
  "operation": "vector_inner_product_search",
  "table": "user_preferences",
  "vector_column": "preference_vector",
  "query_vector": "目标用户的偏好向量",
  "limit": 10,
  "where": {
    "active": true,
    "age_group": "25-35"
  }
}
```

## 性能优化建议

### 1. 索引选择
- **IVFFlat**: 适合高召回率要求，查询速度一般（100ms级别）
- **HNSW**: 高性能和高召回率，但内存占用更多，构建时间较长

### 2. 索引参数调优

#### IVFFlat参数
```json
{
  "index_params": {
    "lists": "100"  // 建议为行数开平方根
  }
}
```

#### HNSW参数
```json
{
  "index_params": {
    "m": "16",              // 连接数，影响召回率和内存
    "ef_construction": "64"  // 构建时检查的近邻数
  }
}
```

### 3. 查询优化

设置查询时的ef_search参数：
```sql
SET hnsw.ef_search = 100;  -- 增加查询召回率
```

### 4. 批量操作建议

- 使用批量插入而非单条插入
- 在索引创建前插入大量数据
- 定期使用VACUUM和ANALYZE优化表

### 5. 内存配置

```sql
-- 调整PostgreSQL内存设置
SET shared_buffers = '1GB';
SET work_mem = '256MB';
SET maintenance_work_mem = '1GB';
```

### 6. 监控和维护

```sql
-- 检查索引使用情况
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read 
FROM pg_stat_user_indexes 
WHERE indexname LIKE '%vector%';

-- 检查表和索引大小
SELECT pg_size_pretty(pg_total_relation_size('documents')) as table_size,
       pg_size_pretty(pg_relation_size('idx_documents_embedding_hnsw')) as index_size;
```

## 注意事项

1. **维度限制**: PGVector最高支持16000维度向量
2. **数据类型**: 向量数据必须是浮点数格式
3. **索引策略**: 根据数据规模和查询需求选择合适的索引类型
4. **内存管理**: HNSW索引会占用较多内存，需要合理配置
5. **查询性能**: 使用适当的WHERE条件可以提高查询效率

这套向量数据库操作系统提供了完整的向量存储、索引和搜索功能，适用于各种AI应用场景，如文档搜索、图像识别、推荐系统等。 