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
} 