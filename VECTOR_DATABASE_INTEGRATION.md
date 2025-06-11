# 向量数据库功能集成

本项目已成功集成PostgreSQL PGVector插件功能，提供完整的向量数据库操作支持。

## 集成概述

基于阿里云PolarDB PostgreSQL的[PGVector文档](https://help.aliyun.com/zh/polardb/polardb-for-postgresql/pgvector)，我们扩展了现有的DSL架构，添加了13种向量数据库操作，支持高维向量存储、索引和相似度搜索。

## 核心特性

### 1. 向量存储能力
- **维度支持**: 1-16000维向量
- **数据类型**: PostgreSQL vector类型
- **索引算法**: IVFFlat和HNSW
- **相似度计算**: L2距离、余弦相似度、内积

### 2. 集成架构

```
TableAndDataUtil (主入口)
    ├── VectorDataOperation (接口)
    ├── VectorDataUtil (实现类)
    ├── JsonDataOperation (已有)
    ├── JsonDataUtil (已有)
    └── GraphDatabaseUtil (已有)
```

### 3. 新增文件

1. **VectorDataOperation.java** - 向量操作接口
2. **VectorDataUtil.java** - 向量操作实现类
3. **VECTOR_OPERATIONS_EXAMPLES.md** - 使用示例文档
4. **VECTOR_DATABASE_INTEGRATION.md** - 集成说明文档

## 支持的操作类型

### 表和数据管理
1. `create_vector_table` - 创建向量表
2. `insert_vector_data` - 插入向量数据
3. `insert_batch_vector_data` - 批量插入向量数据

### 索引管理
4. `create_vector_index` - 创建向量索引
5. `drop_vector_index` - 删除向量索引

### 相似度搜索
6. `vector_l2_search` - L2距离搜索（欧几里得距离）
7. `vector_cosine_search` - 余弦距离搜索
8. `vector_inner_product_search` - 内积搜索（点积）

### 聚合和分析
9. `vector_aggregation` - 向量聚合操作
10. `vector_dimension_analysis` - 向量维度分析

### 工具函数
11. `normalize_vector` - 向量归一化
12. `vector_math` - 向量数学运算
13. `convert_vector_type` - 向量类型转换

## 技术实现要点

### 1. 设计模式
- **单例模式**: VectorDataUtil采用线程安全的单例实现
- **策略模式**: 不同相似度算法使用统一接口
- **模板方法**: 事务处理的统一模式

### 2. 数据处理
- **向量格式化**: 支持数组和字符串格式的向量数据
- **类型转换**: 自动处理向量数据类型转换
- **参数验证**: 严格的维度和参数验证

### 3. 性能优化
- **原生SQL**: 使用原生PostgreSQL SQL以获得最佳性能
- **批量操作**: 支持批量插入和批量更新
- **索引优化**: 提供IVFFlat和HNSW索引参数调优

### 4. 错误处理
- **异常处理**: 完整的异常处理机制
- **参数验证**: 输入参数的严格验证
- **日志记录**: 详细的操作日志记录

## 应用场景

### 1. 文档搜索系统
```json
{
  "operation": "vector_cosine_search",
  "table": "documents",
  "vector_column": "content_embedding",
  "query_vector": [0.1, 0.2, 0.3, ...],
  "limit": 10
}
```

### 2. 图像相似性搜索
```json
{
  "operation": "vector_l2_search",
  "table": "images",
  "vector_column": "feature_vector",
  "query_vector": [0.2, -0.1, 0.4, ...],
  "limit": 20
}
```

### 3. 推荐系统
```json
{
  "operation": "vector_inner_product_search",
  "table": "user_preferences",
  "vector_column": "preference_vector",
  "query_vector": [0.3, 0.1, -0.2, ...],
  "limit": 5
}
```

## 性能特性

### 1. 索引性能
- **IVFFlat**: 快速构建，适中的查询性能，高召回率
- **HNSW**: 高性能查询，优秀召回率，较大内存占用

### 2. 查询性能
- **分页支持**: 完整的分页查询功能
- **条件过滤**: 支持WHERE条件过滤
- **排序优化**: 按相似度自动排序

### 3. 扩展性
- **事务支持**: 完整的事务处理支持
- **并发安全**: 线程安全的实现
- **内存管理**: 优化的内存使用

## 最佳实践

### 1. 索引选择
- 数据量 < 100万：使用IVFFlat
- 数据量 > 100万且对性能要求高：使用HNSW
- 内存受限环境：使用IVFFlat

### 2. 参数调优
```json
// IVFFlat索引
{
  "index_params": {
    "lists": "100"  // 建议为sqrt(行数)
  }
}

// HNSW索引
{
  "index_params": {
    "m": "16",              // 16-64之间
    "ef_construction": "64"  // 64-200之间
  }
}
```

### 3. 查询优化
- 使用适当的WHERE条件减少搜索范围
- 设置合理的limit值
- 使用分页避免大结果集

## 与现有功能的协同

### 1. JSON/JSONB操作
- 向量元数据可以存储为JSON格式
- 支持向量和JSON数据的混合查询

### 2. 图数据库操作
- 向量可以作为图节点的属性
- 支持基于向量相似度的图遍历

### 3. 传统关系型操作
- 向量表支持所有标准SQL操作
- 可以与传统表进行JOIN查询

## 监控和维护

### 1. 性能监控
```sql
-- 检查索引使用情况
SELECT * FROM pg_stat_user_indexes WHERE indexname LIKE '%vector%';

-- 检查查询性能
EXPLAIN ANALYZE SELECT * FROM documents ORDER BY embedding <-> '[0.1,0.2,0.3]' LIMIT 10;
```

### 2. 维护建议
- 定期执行VACUUM ANALYZE
- 监控索引大小和内存使用
- 根据查询模式调整索引参数

## 未来扩展

### 1. 计划功能
- 向量相似度阈值查询
- 多向量聚合搜索
- 向量数据导入导出工具

### 2. 性能优化
- 查询结果缓存
- 异步索引构建
- 分布式向量搜索

这套向量数据库集成为现有的DSL系统提供了强大的AI能力支持，能够满足现代应用对向量搜索和相似度计算的需求。 