package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import org.jooq.DSLContext;

/**
 * Apache AGE 图数据库操作接口
 * 支持Cypher查询语言和图数据库的创建、节点、边操作等
 * 
 * @author acproject@qq.com
 * @date 2025-01-19
 */
public interface GraphDatabaseOperation {
    
    // =============== 图管理操作 ===============
    
    /**
     * 创建图数据库
     * JSON格式: {"operation": "create_graph", "graph_name": "test_graph"}
     */
    DataRecord createGraph(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 删除图数据库
     * JSON格式: {"operation": "drop_graph", "graph_name": "test_graph", "cascade": true}
     */
    DataRecord dropGraph(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 获取图统计信息
     * JSON格式: {"operation": "graph_stats", "graph_name": "test_graph"}
     */
    DataRecord getGraphStats(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 节点标签操作 ===============
    
    /**
     * 创建节点标签
     * JSON格式: {"operation": "create_vertex_label", "graph_name": "test_graph", "label": "Person"}
     */
    DataRecord createVertexLabel(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 创建边标签
     * JSON格式: {"operation": "create_edge_label", "graph_name": "test_graph", "label": "KNOWS"}
     */
    DataRecord createEdgeLabel(DSLContext dslContext, JsonNode rootNode);
    
    // =============== Cypher查询操作 ===============
    
    /**
     * 执行Cypher CREATE操作
     * JSON格式: {"operation": "cypher_create", "graph_name": "test_graph", "cypher": "CREATE (n:Person {name: 'Alice', age: 30}) RETURN n"}
     */
    DataRecord cypherCreate(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 执行Cypher MATCH查询操作
     * JSON格式: {"operation": "cypher_match", "graph_name": "test_graph", "cypher": "MATCH (n:Person) RETURN n", "pagination": {"page": 1, "pageSize": 10}}
     */
    DataRecord cypherMatch(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 执行Cypher MERGE操作
     * JSON格式: {"operation": "cypher_merge", "graph_name": "test_graph", "cypher": "MERGE (n:Person {name: 'Bob'}) RETURN n"}
     */
    DataRecord cypherMerge(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 执行Cypher DELETE操作
     * JSON格式: {"operation": "cypher_delete", "graph_name": "test_graph", "cypher": "MATCH (n:Person {name: 'Alice'}) DELETE n"}
     */
    DataRecord cypherDelete(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 执行Cypher SET操作（更新节点/边属性）
     * JSON格式: {"operation": "cypher_set", "graph_name": "test_graph", "cypher": "MATCH (n:Person {name: 'Alice'}) SET n.age = 31 RETURN n"}
     */
    DataRecord cypherSet(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 批量操作 ===============
    
    /**
     * 批量创建节点
     * JSON格式: {"operation": "batch_create_nodes", "graph_name": "test_graph", "label": "Person", "nodes": [...]}
     */
    DataRecord batchCreateNodes(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 批量创建边
     * JSON格式: {"operation": "batch_create_edges", "graph_name": "test_graph", "edge_label": "KNOWS", "edges": [...]}
     */
    DataRecord batchCreateEdges(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 数据加载操作 ===============
    
    /**
     * 从文件加载节点数据
     * JSON格式: {"operation": "load_vertices_from_file", "graph_name": "test_graph", "label": "Person", "file_path": "/path/to/file.csv", "id_column": true}
     */
    DataRecord loadVerticesFromFile(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 从文件加载边数据
     * JSON格式: {"operation": "load_edges_from_file", "graph_name": "test_graph", "edge_label": "KNOWS", "file_path": "/path/to/file.csv"}
     */
    DataRecord loadEdgesFromFile(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 路径查询操作 ===============
    
    /**
     * 查找最短路径
     * JSON格式: {"operation": "shortest_path", "graph_name": "test_graph", "start_node": {...}, "end_node": {...}, "relationship": "KNOWS"}
     */
    DataRecord findShortestPath(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 查找所有路径
     * JSON格式: {"operation": "all_paths", "graph_name": "test_graph", "start_node": {...}, "end_node": {...}, "max_depth": 5}
     */
    DataRecord findAllPaths(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 通用Cypher执行 ===============
    
    /**
     * 执行任意Cypher查询
     * JSON格式: {"operation": "execute_cypher", "graph_name": "test_graph", "cypher": "...", "use_transaction": true}
     */
    DataRecord executeCypher(DSLContext dslContext, JsonNode rootNode);

    // =============== 高级图分析操作 ===============
    
    /**
     * 计算节点的度数（入度、出度、总度数）
     * JSON 格式:
     * {
     *   "operation": "node_degree",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "node_properties": {"name": "Alice"},
     *   "degree_type": "all|in|out",
     *   "edge_label": "FOLLOWS"
     * }
     */
    DataRecord calculateNodeDegree(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 查找图中的连通分量
     * JSON 格式:
     * {
     *   "operation": "connected_components",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "edge_label": "KNOWS",
     *   "max_components": 10
     * }
     */
    DataRecord findConnectedComponents(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 计算节点的中心性指标（度中心性、接近中心性、介数中心性）
     * JSON 格式:
     * {
     *   "operation": "centrality_analysis",
     *   "graph_name": "social_network",
     *   "centrality_type": "degree|closeness|betweenness",
     *   "node_label": "Person",
     *   "edge_label": "KNOWS",
     *   "limit": 20
     * }
     */
    DataRecord calculateCentrality(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 查找图中的三角形（三元闭包）
     * JSON 格式:
     * {
     *   "operation": "find_triangles",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "edge_label": "KNOWS",
     *   "limit": 100
     * }
     */
    DataRecord findTriangles(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 社区检测算法（标签传播算法）
     * JSON 格式:
     * {
     *   "operation": "community_detection",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "edge_label": "KNOWS",
     *   "max_iterations": 10,
     *   "min_community_size": 3
     * }
     */
    DataRecord detectCommunities(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 图的模式匹配
     * JSON 格式:
     * {
     *   "operation": "pattern_matching",
     *   "graph_name": "social_network",
     *   "pattern": "(a:Person)-[:KNOWS]->(b:Person)-[:KNOWS]->(c:Person)",
     *   "where_conditions": "a.age > 25 AND b.city = 'Beijing'",
     *   "return_clause": "a.name, b.name, c.name",
     *   "limit": 50
     * }
     */
    DataRecord patternMatching(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 图的子图查询
     * JSON 格式:
     * {
     *   "operation": "subgraph_query",
     *   "graph_name": "social_network",
     *   "center_node": {"label": "Person", "properties": {"name": "Alice"}},
     *   "max_depth": 2,
     *   "edge_labels": ["KNOWS", "FOLLOWS"],
     *   "include_properties": true
     * }
     */
    DataRecord querySubgraph(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 图的聚合统计
     * JSON 格式:
     * {
     *   "operation": "graph_aggregation",
     *   "graph_name": "social_network",
     *   "aggregation_type": "node_count|edge_count|avg_degree|density",
     *   "group_by": "label",
     *   "filters": {"node_label": "Person", "edge_label": "KNOWS"}
     * }
     */
    DataRecord aggregateGraph(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 图数据导入导出操作 ===============
    
    /**
     * 导出图数据为JSON格式
     * JSON 格式:
     * {
     *   "operation": "export_graph_json",
     *   "graph_name": "social_network",
     *   "export_format": "cytoscape|d3|networkx",
     *   "include_properties": true,
     *   "filters": {"node_labels": ["Person"], "edge_labels": ["KNOWS"]}
     * }
     */
    DataRecord exportGraphToJson(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 从JSON数据导入图
     * JSON 格式:
     * {
     *   "operation": "import_graph_json",
     *   "graph_name": "social_network",
     *   "json_data": {...},
     *   "merge_strategy": "create|merge|replace",
     *   "batch_size": 1000
     * }
     */
    DataRecord importGraphFromJson(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 图与向量数据混合操作 ===============
    
    /**
     * 为图节点添加向量嵌入
     * JSON 格式:
     * {
     *   "operation": "add_node_embeddings",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "embedding_property": "embedding",
     *   "vector_dimension": 128,
     *   "embedding_data": [{"node_id": "id1", "vector": [0.1, 0.2, ...]}, ...]
     * }
     */
    DataRecord addNodeEmbeddings(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 基于向量相似度查找相似节点
     * JSON 格式:
     * {
     *   "operation": "find_similar_nodes",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "embedding_property": "embedding",
     *   "query_vector": [0.1, 0.2, 0.3, ...],
     *   "similarity_metric": "cosine|l2|inner_product",
     *   "limit": 10,
     *   "threshold": 0.8
     * }
     */
    DataRecord findSimilarNodes(DSLContext dslContext, JsonNode rootNode);
    
    // =============== 图与JSON数据混合操作 ===============
    
    /**
     * 查询包含特定JSON属性的节点
     * JSON 格式:
     * {
     *   "operation": "query_nodes_with_json",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "json_property": "metadata",
     *   "json_path": "$.profile.interests",
     *   "json_value": "technology",
     *   "json_operator": "contains|equals|exists"
     * }
     */
    DataRecord queryNodesWithJson(DSLContext dslContext, JsonNode rootNode);
    
    /**
     * 更新节点的JSON属性
     * JSON 格式:
     * {
     *   "operation": "update_node_json",
     *   "graph_name": "social_network",
     *   "node_label": "Person",
     *   "node_filter": {"name": "Alice"},
     *   "json_property": "metadata",
     *   "json_updates": [
     *     {"path": "$.profile.age", "value": 30, "operation": "set"},
     *     {"path": "$.profile.interests", "value": "AI", "operation": "append"}
     *   ]
     * }
     */
    DataRecord updateNodeJson(DSLContext dslContext, JsonNode rootNode);
}