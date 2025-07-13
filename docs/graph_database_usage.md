# Apache AGE 图数据库操作指南

## 概述

本项目已扩展支持Apache AGE图数据库操作，您可以通过JSON DSL进行各种图数据库操作，包括创建图、管理节点和边、执行Cypher查询等。

## 前置条件

1. 确保PostgreSQL数据库已安装Apache AGE扩展
2. 在您的数据库中执行以下SQL启用AGE：
```sql
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path TO ag_catalog, "$user", public;
```

## 支持的操作类型

### 1. 图管理操作

#### 创建图
```json
{
  "operation": "create_graph",
  "graph_name": "my_graph",
  "use_transaction": false
}
```

#### 删除图
```json
{
  "operation": "drop_graph",
  "graph_name": "my_graph",
  "cascade": true,
  "use_transaction": false
}
```

#### 获取图统计信息
```json
{
  "operation": "graph_stats",
  "graph_name": "my_graph"
}
```

### 2. 标签管理

#### 创建节点标签
```json
{
  "operation": "create_vertex_label",
  "graph_name": "my_graph",
  "label": "Person",
  "use_transaction": false
}
```

#### 创建边标签
```json
{
  "operation": "create_edge_label",
  "graph_name": "my_graph",
  "label": "KNOWS",
  "use_transaction": false
}
```

### 3. Cypher操作

#### CREATE - 创建节点和边
```json
{
  "operation": "cypher_create",
  "graph_name": "social_network",
  "cypher": "CREATE (alice:Person {name: 'Alice', age: 30}) RETURN alice",
  "use_transaction": true
}
```

#### MATCH - 查询数据（支持分页）
```json
{
  "operation": "cypher_match",
  "graph_name": "social_network",
  "cypher": "MATCH (p:Person) WHERE p.age > 25 RETURN p",
  "pagination": {
    "page": 1,
    "pageSize": 10
  },
  "use_transaction": false
}
```

#### MERGE - 合并操作
```json
{
  "operation": "cypher_merge",
  "graph_name": "social_network",
  "cypher": "MERGE (bob:Person {name: 'Bob'}) ON CREATE SET bob.age = 25 RETURN bob",
  "use_transaction": true
}
```

#### SET - 更新属性
```json
{
  "operation": "cypher_set",
  "graph_name": "social_network",
  "cypher": "MATCH (p:Person {name: 'Alice'}) SET p.age = 31 RETURN p",
  "use_transaction": true
}
```

#### DELETE - 删除节点或边
```json
{
  "operation": "cypher_delete",
  "graph_name": "social_network",
  "cypher": "MATCH (p:Person {name: 'Alice'}) DELETE p",
  "use_transaction": true
}
```

### 4. 批量操作

#### 批量创建节点
```json
{
  "operation": "batch_create_nodes",
  "graph_name": "social_network",
  "label": "Person",
  "nodes": [
    {"name": "Alice", "age": 30},
    {"name": "Bob", "age": 25},
    {"name": "Charlie", "age": 35}
  ],
  "use_transaction": true
}
```

#### 批量创建边
```json
{
  "operation": "batch_create_edges",
  "graph_name": "social_network",
  "edge_label": "KNOWS",
  "edges": [
    {
      "start_node_id": "1125899906842625",
      "end_node_id": "1125899906842626",
      "properties": {"since": "2020-01-01"}
    }
  ],
  "use_transaction": true
}
```

### 5. 数据文件加载

#### 从文件加载节点
```json
{
  "operation": "load_vertices_from_file",
  "graph_name": "my_graph",
  "label": "Person",
  "file_path": "/path/to/vertices.csv",
  "id_column": true,
  "use_transaction": true
}
```

#### 从文件加载边
```json
{
  "operation": "load_edges_from_file",
  "graph_name": "my_graph",
  "edge_label": "KNOWS",
  "file_path": "/path/to/edges.csv",
  "use_transaction": true
}
```

### 6. 路径查询

#### 查找最短路径
```json
{
  "operation": "shortest_path",
  "graph_name": "social_network",
  "start_node": {
    "label": "Person",
    "properties": {"name": "Alice"}
  },
  "end_node": {
    "label": "Person",
    "properties": {"name": "Bob"}
  },
  "relationship": "KNOWS"
}
```

#### 查找所有路径
```json
{
  "operation": "all_paths",
  "graph_name": "social_network",
  "start_node": {
    "label": "Person",
    "properties": {"name": "Alice"}
  },
  "end_node": {
    "label": "Person",
    "properties": {"name": "Charlie"}
  },
  "max_depth": 3
}
```

### 7. 通用Cypher执行

#### 执行任意Cypher查询
```json
{
  "operation": "execute_cypher",
  "graph_name": "social_network",
  "cypher": "MATCH (p:Person) WHERE p.age BETWEEN 25 AND 35 RETURN p.name, p.age ORDER BY p.age",
  "use_transaction": false
}
```

## 使用示例

### Java代码示例

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jooq.DSLContext;

@Service
public class GraphService {

    @Autowired
    private DSLContext dslContext;

    public void createSocialNetwork() throws Exception {
        // 1. 创建图
        String createGraphJson = """
            {
              "operation": "create_graph",
              "graph_name": "social_network",
              "use_transaction": false
            }
            """;
        TableAndDataUtil.processRequest(dslContext, createGraphJson);

        // 2. 创建节点标签
        String createLabelJson = """
            {
              "operation": "create_vertex_label",
              "graph_name": "social_network",
              "label": "Person",
              "use_transaction": false
            }
            """;
        TableAndDataUtil.processRequest(dslContext, createLabelJson);

        // 3. 创建节点
        String createNodeJson = """
            {
              "operation": "cypher_create",
              "graph_name": "social_network",
              "cypher": "CREATE (alice:Person {name: 'Alice', age: 30}) RETURN alice",
              "use_transaction": true
            }
            """;
        DataRecord result = TableAndDataUtil.processRequest(dslContext, createNodeJson);
        System.out.println("节点创建结果: " + result);

        // 4. 查询节点
        String queryJson = """
            {
              "operation": "cypher_match",
              "graph_name": "social_network",
              "cypher": "MATCH (p:Person) RETURN p.name, p.age",
              "use_transaction": false
            }
            """;
        DataRecord queryResult = TableAndDataUtil.processRequest(dslContext, queryJson);
        System.out.println("查询结果: " + queryResult);
    }
}
```

## 高级功能

### 复杂查询示例

#### 1. 朋友推荐（二度人脉）
```json
{
  "operation": "cypher_match",
  "graph_name": "social_network",
  "cypher": "MATCH (me:Person {name: 'Alice'})-[:KNOWS]->(friend)-[:KNOWS]->(friendOfFriend) WHERE friendOfFriend <> me AND NOT (me)-[:KNOWS]->(friendOfFriend) RETURN DISTINCT friendOfFriend.name as recommended_friend",
  "use_transaction": false
}
```

#### 2. 影响力分析（度中心性）
```json
{
  "operation": "cypher_match",
  "graph_name": "social_network",
  "cypher": "MATCH (p:Person) OPTIONAL MATCH (p)-[:KNOWS]->(connected) RETURN p.name, count(connected) as connections ORDER BY connections DESC",
  "use_transaction": false
}
```

#### 3. 社区检测（连通分量）
```json
{
  "operation": "cypher_match",
  "graph_name": "social_network",
  "cypher": "MATCH path = (start:Person)-[:KNOWS*]-(connected:Person) RETURN start.name, collect(DISTINCT connected.name) as community",
  "use_transaction": false
}
```

## 注意事项

1. **事务使用**: 对于创建、更新、删除操作，建议设置 `"use_transaction": true`
2. **分页查询**: 大数据集查询时使用分页避免内存溢出
3. **Cypher语法**: 遵循Apache AGE支持的Cypher语法规范
4. **性能优化**: 为频繁查询的属性创建索引
5. **错误处理**: 所有操作都有异常处理，会抛出详细的错误信息

## 性能建议

1. **批量操作**: 大量数据插入时使用批量创建操作
2. **索引使用**: 对查询频繁的属性创建索引
3. **查询优化**: 使用EXPLAIN分析查询性能
4. **内存管理**: 大结果集查询使用分页
5. **连接池**: 配置合适的数据库连接池

## 示例文件

项目提供了完整的示例文件：
- `examples/json/graph_operations.json` - 完整功能演示
- `examples/json/simple_graph_demo.json` - 简单操作示例
- `examples/age_test.sql` - SQL测试脚本

通过这些示例，您可以快速上手图数据库操作功能。 