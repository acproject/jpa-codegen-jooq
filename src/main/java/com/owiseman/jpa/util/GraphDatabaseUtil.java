package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.owiseman.jpa.model.DataRecord;
import com.owiseman.jpa.model.PaginationInfo;
import lombok.extern.log4j.Log4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Apache AGE 图数据库操作实现类
 * 支持Cypher查询语言和图数据库的各种操作
 * 
 * @author acproject@qq.com
 * @date 2025-01-19
 */
@Log4j
public class GraphDatabaseUtil implements GraphDatabaseOperation {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static volatile GraphDatabaseUtil instance;

    private GraphDatabaseUtil() {
    }

    public static GraphDatabaseUtil getInstance() {
        if (instance == null) {
            synchronized (GraphDatabaseUtil.class) {
                if (instance == null) {
                    instance = new GraphDatabaseUtil();
                }
            }
        }
        return instance;
    }

    // =============== 图管理操作 ===============

    @Override
    public DataRecord createGraph(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(createGraphLogic(configuration.dsl(), graphName));
            });
        } else {
            result.set(createGraphLogic(dslContext, graphName));
        }
        
        return result.get();
    }

    private DataRecord createGraphLogic(DSLContext dslContext, String graphName) {
        try {
            // 执行 LOAD 'age' 和设置搜索路径
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 创建图
            String sql = "SELECT create_graph(?)";
            dslContext.execute(sql, graphName);
            
            return new DataRecord("create_graph", graphName, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("创建图数据库失败: " + graphName, e);
            throw new RuntimeException("创建图数据库失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord dropGraph(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        boolean cascade = rootNode.has("cascade") && rootNode.get("cascade").asBoolean();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(dropGraphLogic(configuration.dsl(), graphName, cascade));
            });
        } else {
            result.set(dropGraphLogic(dslContext, graphName, cascade));
        }
        
        return result.get();
    }

    private DataRecord dropGraphLogic(DSLContext dslContext, String graphName, boolean cascade) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT drop_graph(?, ?)";
            dslContext.execute(sql, graphName, cascade);
            
            return new DataRecord("drop_graph", graphName, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("删除图数据库失败: " + graphName, e);
            throw new RuntimeException("删除图数据库失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord getGraphStats(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = "return graph_stats('" + graphName + "')";
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (stats agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("graph_stats", graphName, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("获取图统计信息失败: " + graphName, e);
            throw new RuntimeException("获取图统计信息失败: " + e.getMessage(), e);
        }
    }

    // =============== 节点标签操作 ===============

    @Override
    public DataRecord createVertexLabel(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String label = rootNode.get("label").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(createVertexLabelLogic(configuration.dsl(), graphName, label));
            });
        } else {
            result.set(createVertexLabelLogic(dslContext, graphName, label));
        }
        
        return result.get();
    }

    private DataRecord createVertexLabelLogic(DSLContext dslContext, String graphName, String label) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT create_vlabel(?, ?)";
            dslContext.execute(sql, graphName, label);
            
            return new DataRecord("create_vertex_label", label, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("创建节点标签失败: " + label, e);
            throw new RuntimeException("创建节点标签失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord createEdgeLabel(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String label = rootNode.get("label").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(createEdgeLabelLogic(configuration.dsl(), graphName, label));
            });
        } else {
            result.set(createEdgeLabelLogic(dslContext, graphName, label));
        }
        
        return result.get();
    }

    private DataRecord createEdgeLabelLogic(DSLContext dslContext, String graphName, String label) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT create_elabel(?, ?)";
            dslContext.execute(sql, graphName, label);
            
            return new DataRecord("create_edge_label", label, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("创建边标签失败: " + label, e);
            throw new RuntimeException("创建边标签失败: " + e.getMessage(), e);
        }
    }

    // =============== Cypher查询操作 ===============

    @Override
    public DataRecord cypherCreate(DSLContext dslContext, JsonNode rootNode) {
        return executeCypherOperation(dslContext, rootNode, "cypher_create");
    }

    @Override
    public DataRecord cypherMatch(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String cypherQuery = rootNode.get("cypher").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        // 处理分页
        final PaginationInfo pagination;
        if (rootNode.has("pagination")) {
            JsonNode paginationNode = rootNode.get("pagination");
            int page = paginationNode.get("page").asInt();
            int pageSize = paginationNode.get("pageSize").asInt();
            // 暂时设置totalItems为0，totalPages为0，因为这些值在查询前无法确定
            pagination = new PaginationInfo(page, pageSize, 0, 0);
        } else {
            pagination = null;
        }
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(cypherMatchLogic(configuration.dsl(), graphName, cypherQuery, pagination));
            });
        } else {
            result.set(cypherMatchLogic(dslContext, graphName, cypherQuery, pagination));
        }
        
        return result.get();
    }

    private DataRecord cypherMatchLogic(DSLContext dslContext, String graphName, String cypherQuery, PaginationInfo pagination) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 如果有分页，修改Cypher查询
            if (pagination != null) {
                int offset = (pagination.currentPage() - 1) * pagination.pageSize();
                cypherQuery += " SKIP " + offset + " LIMIT " + pagination.pageSize();
            }
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (result agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("cypher_match", cypherQuery, Optional.of(data), Optional.ofNullable(pagination));
        } catch (Exception e) {
            log.error("Cypher查询失败: " + cypherQuery, e);
            throw new RuntimeException("Cypher查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord cypherMerge(DSLContext dslContext, JsonNode rootNode) {
        return executeCypherOperation(dslContext, rootNode, "cypher_merge");
    }

    @Override
    public DataRecord cypherDelete(DSLContext dslContext, JsonNode rootNode) {
        return executeCypherOperation(dslContext, rootNode, "cypher_delete");
    }

    @Override
    public DataRecord cypherSet(DSLContext dslContext, JsonNode rootNode) {
        return executeCypherOperation(dslContext, rootNode, "cypher_set");
    }

    private DataRecord executeCypherOperation(DSLContext dslContext, JsonNode rootNode, String operationType) {
        String graphName = rootNode.get("graph_name").asText();
        String cypherQuery = rootNode.get("cypher").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(executeCypherLogic(configuration.dsl(), graphName, cypherQuery, operationType));
            });
        } else {
            result.set(executeCypherLogic(dslContext, graphName, cypherQuery, operationType));
        }
        
        return result.get();
    }

    private DataRecord executeCypherLogic(DSLContext dslContext, String graphName, String cypherQuery, String operationType) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (result agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord(operationType, cypherQuery, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("Cypher操作失败 [" + operationType + "]: " + cypherQuery, e);
            throw new RuntimeException("Cypher操作失败: " + e.getMessage(), e);
        }
    }

    // =============== 批量操作 ===============

    @Override
    public DataRecord batchCreateNodes(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String label = rootNode.get("label").asText();
        JsonNode nodes = rootNode.get("nodes");
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(batchCreateNodesLogic(configuration.dsl(), graphName, label, nodes));
            });
        } else {
            result.set(batchCreateNodesLogic(dslContext, graphName, label, nodes));
        }
        
        return result.get();
    }

    private DataRecord batchCreateNodesLogic(DSLContext dslContext, String graphName, String label, JsonNode nodes) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            StringBuilder cypherQuery = new StringBuilder();
            for (JsonNode node : nodes) {
                if (cypherQuery.length() > 0) {
                    cypherQuery.append(", ");
                }
                cypherQuery.append("(:").append(label).append(" ").append(node.toString()).append(")");
            }
            
            String fullQuery = "CREATE " + cypherQuery.toString();
            String sql = "SELECT * FROM cypher(?, $$" + fullQuery + "$$) as (result agtype)";
            dslContext.execute(sql, graphName);
            
            return new DataRecord("batch_create_nodes", label + " (" + nodes.size() + " nodes)", Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("批量创建节点失败: " + label, e);
            throw new RuntimeException("批量创建节点失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord batchCreateEdges(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        JsonNode edges = rootNode.get("edges");
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(batchCreateEdgesLogic(configuration.dsl(), graphName, edgeLabel, edges));
            });
        } else {
            result.set(batchCreateEdgesLogic(dslContext, graphName, edgeLabel, edges));
        }
        
        return result.get();
    }

    private DataRecord batchCreateEdgesLogic(DSLContext dslContext, String graphName, String edgeLabel, JsonNode edges) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            for (JsonNode edge : edges) {
                String startNodeId = edge.get("start_node_id").asText();
                String endNodeId = edge.get("end_node_id").asText();
                JsonNode properties = edge.has("properties") ? edge.get("properties") : null;
                
                String cypherQuery = "MATCH (a), (b) WHERE id(a) = " + startNodeId + " AND id(b) = " + endNodeId +
                    " CREATE (a)-[r:" + edgeLabel;
                
                if (properties != null) {
                    cypherQuery += " " + properties.toString();
                }
                cypherQuery += "]->(b) RETURN r";
                
                String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (result agtype)";
                dslContext.execute(sql, graphName);
            }
            
            return new DataRecord("batch_create_edges", edgeLabel + " (" + edges.size() + " edges)", Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("批量创建边失败: " + edgeLabel, e);
            throw new RuntimeException("批量创建边失败: " + e.getMessage(), e);
        }
    }

    // =============== 数据加载操作 ===============

    @Override
    public DataRecord loadVerticesFromFile(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String label = rootNode.get("label").asText();
        String filePath = rootNode.get("file_path").asText();
        boolean idColumn = rootNode.has("id_column") && rootNode.get("id_column").asBoolean();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(loadVerticesFromFileLogic(configuration.dsl(), graphName, label, filePath, idColumn));
            });
        } else {
            result.set(loadVerticesFromFileLogic(dslContext, graphName, label, filePath, idColumn));
        }
        
        return result.get();
    }

    private DataRecord loadVerticesFromFileLogic(DSLContext dslContext, String graphName, String label, String filePath, boolean idColumn) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT load_labels_from_file(?, ?, ?, ?)";
            dslContext.execute(sql, graphName, label, filePath, idColumn);
            
            return new DataRecord("load_vertices_from_file", label + " from " + filePath, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("从文件加载节点失败: " + filePath, e);
            throw new RuntimeException("从文件加载节点失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord loadEdgesFromFile(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        String filePath = rootNode.get("file_path").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(loadEdgesFromFileLogic(configuration.dsl(), graphName, edgeLabel, filePath));
            });
        } else {
            result.set(loadEdgesFromFileLogic(dslContext, graphName, edgeLabel, filePath));
        }
        
        return result.get();
    }

    private DataRecord loadEdgesFromFileLogic(DSLContext dslContext, String graphName, String edgeLabel, String filePath) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String sql = "SELECT load_edges_from_file(?, ?, ?)";
            dslContext.execute(sql, graphName, edgeLabel, filePath);
            
            return new DataRecord("load_edges_from_file", edgeLabel + " from " + filePath, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("从文件加载边失败: " + filePath, e);
            throw new RuntimeException("从文件加载边失败: " + e.getMessage(), e);
        }
    }

    // =============== 路径查询操作 ===============

    @Override
    public DataRecord findShortestPath(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        JsonNode startNode = rootNode.get("start_node");
        JsonNode endNode = rootNode.get("end_node");
        String relationship = rootNode.has("relationship") ? rootNode.get("relationship").asText() : "";
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildShortestPathQuery(startNode, endNode, relationship);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (path agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("shortest_path", "from " + startNode + " to " + endNode, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查找最短路径失败", e);
            throw new RuntimeException("查找最短路径失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord findAllPaths(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        JsonNode startNode = rootNode.get("start_node");
        JsonNode endNode = rootNode.get("end_node");
        int maxDepth = rootNode.has("max_depth") ? rootNode.get("max_depth").asInt() : 5;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildAllPathsQuery(startNode, endNode, maxDepth);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (path agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("all_paths", "from " + startNode + " to " + endNode, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查找所有路径失败", e);
            throw new RuntimeException("查找所有路径失败: " + e.getMessage(), e);
        }
    }

    // =============== 通用Cypher执行 ===============

    @Override
    public DataRecord executeCypher(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String cypherQuery = rootNode.get("cypher").asText();
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(executeCypherLogic(configuration.dsl(), graphName, cypherQuery, "execute_cypher"));
            });
        } else {
            result.set(executeCypherLogic(dslContext, graphName, cypherQuery, "execute_cypher"));
        }
        
        return result.get();
    }

    // =============== 辅助方法 ===============

    private List<Map<String, Object>> convertToMapList(Result<Record> result) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Record record : result) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < record.size(); i++) {
                map.put(record.field(i).getName(), record.get(i));
            }
            list.add(map);
        }
        return list;
    }

    private String buildShortestPathQuery(JsonNode startNode, JsonNode endNode, String relationship) {
        StringBuilder query = new StringBuilder("MATCH p = shortestPath((start");
        
        // 添加起始节点条件
        if (startNode.has("label")) {
            query.append(":").append(startNode.get("label").asText());
        }
        if (startNode.has("properties")) {
            query.append(" ").append(startNode.get("properties").toString());
        }
        
        query.append(")-[");
        if (!relationship.isEmpty()) {
            query.append(":").append(relationship);
        }
        query.append("*]->(end");
        
        // 添加结束节点条件
        if (endNode.has("label")) {
            query.append(":").append(endNode.get("label").asText());
        }
        if (endNode.has("properties")) {
            query.append(" ").append(endNode.get("properties").toString());
        }
        
        query.append(")) RETURN p");
        return query.toString();
    }

    private String buildAllPathsQuery(JsonNode startNode, JsonNode endNode, int maxDepth) {
        StringBuilder query = new StringBuilder("MATCH p = (start");
        
        // 添加起始节点条件
        if (startNode.has("label")) {
            query.append(":").append(startNode.get("label").asText());
        }
        if (startNode.has("properties")) {
            query.append(" ").append(startNode.get("properties").toString());
        }
        
        query.append(")-[*1..").append(maxDepth).append("]->(end");
        
        // 添加结束节点条件
        if (endNode.has("label")) {
            query.append(":").append(endNode.get("label").asText());
        }
        if (endNode.has("properties")) {
            query.append(" ").append(endNode.get("properties").toString());
        }
        
        query.append(") RETURN p");
        return query.toString();
    }
} 