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

    // =============== 高级图分析操作实现 ===============

    @Override
    public DataRecord calculateNodeDegree(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        JsonNode nodeProperties = rootNode.get("node_properties");
        String degreeType = rootNode.has("degree_type") ? rootNode.get("degree_type").asText() : "all";
        String edgeLabel = rootNode.has("edge_label") ? rootNode.get("edge_label").asText() : "";
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildNodeDegreeQuery(nodeLabel, nodeProperties, degreeType, edgeLabel);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype, degree agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("node_degree", degreeType + " degree for " + nodeLabel, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("计算节点度数失败", e);
            throw new RuntimeException("计算节点度数失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord findConnectedComponents(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        int maxComponents = rootNode.has("max_components") ? rootNode.get("max_components").asInt() : 10;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = String.format(
                "MATCH (n:%s) " +
                "OPTIONAL MATCH (n)-[:%s*]-(connected:%s) " +
                "WITH n, collect(DISTINCT connected) + [n] as component " +
                "WITH component, size(component) as componentSize " +
                "ORDER BY componentSize DESC " +
                "LIMIT %d " +
                "RETURN component, componentSize",
                nodeLabel, edgeLabel, nodeLabel, maxComponents
            );
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (component agtype, size agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("connected_components", "components in " + graphName, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查找连通分量失败", e);
            throw new RuntimeException("查找连通分量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord calculateCentrality(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String centralityType = rootNode.get("centrality_type").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        int limit = rootNode.has("limit") ? rootNode.get("limit").asInt() : 20;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildCentralityQuery(centralityType, nodeLabel, edgeLabel, limit);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype, centrality agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("centrality_analysis", centralityType + " centrality", Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("计算中心性失败", e);
            throw new RuntimeException("计算中心性失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord findTriangles(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        int limit = rootNode.has("limit") ? rootNode.get("limit").asInt() : 100;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = String.format(
                "MATCH (a:%s)-[:%s]-(b:%s)-[:%s]-(c:%s)-[:%s]-(a) " +
                "WHERE id(a) < id(b) AND id(b) < id(c) " +
                "RETURN a, b, c " +
                "LIMIT %d",
                nodeLabel, edgeLabel, nodeLabel, edgeLabel, nodeLabel, edgeLabel, limit
            );
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (a agtype, b agtype, c agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("find_triangles", "triangles in " + graphName, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查找三角形失败", e);
            throw new RuntimeException("查找三角形失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord detectCommunities(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String edgeLabel = rootNode.get("edge_label").asText();
        int maxIterations = rootNode.has("max_iterations") ? rootNode.get("max_iterations").asInt() : 10;
        int minCommunitySize = rootNode.has("min_community_size") ? rootNode.get("min_community_size").asInt() : 3;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 简化的社区检测算法（基于连通性）
            String cypherQuery = String.format(
                "MATCH (n:%s) " +
                "OPTIONAL MATCH (n)-[:%s*1..%d]-(community:%s) " +
                "WITH n, collect(DISTINCT community) + [n] as communityNodes " +
                "WITH communityNodes, size(communityNodes) as communitySize " +
                "WHERE communitySize >= %d " +
                "RETURN communityNodes, communitySize " +
                "ORDER BY communitySize DESC",
                nodeLabel, edgeLabel, maxIterations, nodeLabel, minCommunitySize
            );
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (community agtype, size agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("community_detection", "communities in " + graphName, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("社区检测失败", e);
            throw new RuntimeException("社区检测失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord patternMatching(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String pattern = rootNode.get("pattern").asText();
        String whereConditions = rootNode.has("where_conditions") ? rootNode.get("where_conditions").asText() : "";
        String returnClause = rootNode.has("return_clause") ? rootNode.get("return_clause").asText() : "*";
        int limit = rootNode.has("limit") ? rootNode.get("limit").asInt() : 50;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            StringBuilder cypherQuery = new StringBuilder("MATCH ").append(pattern);
            if (!whereConditions.isEmpty()) {
                cypherQuery.append(" WHERE ").append(whereConditions);
            }
            cypherQuery.append(" RETURN ").append(returnClause);
            cypherQuery.append(" LIMIT ").append(limit);
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery.toString() + "$$) as (result agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("pattern_matching", "pattern: " + pattern, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("模式匹配失败", e);
            throw new RuntimeException("模式匹配失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord querySubgraph(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        JsonNode centerNode = rootNode.get("center_node");
        int maxDepth = rootNode.has("max_depth") ? rootNode.get("max_depth").asInt() : 2;
        JsonNode edgeLabels = rootNode.get("edge_labels");
        boolean includeProperties = rootNode.has("include_properties") && rootNode.get("include_properties").asBoolean();
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildSubgraphQuery(centerNode, maxDepth, edgeLabels, includeProperties);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (subgraph agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("subgraph_query", "subgraph around center node", Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("子图查询失败", e);
            throw new RuntimeException("子图查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord aggregateGraph(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String aggregationType = rootNode.get("aggregation_type").asText();
        String groupBy = rootNode.has("group_by") ? rootNode.get("group_by").asText() : "";
        JsonNode filters = rootNode.get("filters");
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String cypherQuery = buildAggregationQuery(aggregationType, groupBy, filters);
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (result agtype)";
            
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("graph_aggregation", aggregationType + " aggregation", Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("图聚合失败", e);
            throw new RuntimeException("图聚合失败: " + e.getMessage(), e);
        }
    }

    // =============== 图数据导入导出操作实现 ===============

    @Override
    public DataRecord exportGraphToJson(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String exportFormat = rootNode.has("export_format") ? rootNode.get("export_format").asText() : "networkx";
        boolean includeProperties = rootNode.has("include_properties") && rootNode.get("include_properties").asBoolean();
        JsonNode filters = rootNode.get("filters");
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 导出节点
            String nodeQuery = buildExportNodesQuery(filters, includeProperties);
            String nodeSql = "SELECT * FROM cypher(?, $$" + nodeQuery + "$$) as (nodes agtype)";
            Result<Record> nodeResult = dslContext.fetch(nodeSql, graphName);
            
            // 导出边
            String edgeQuery = buildExportEdgesQuery(filters, includeProperties);
            String edgeSql = "SELECT * FROM cypher(?, $$" + edgeQuery + "$$) as (edges agtype)";
            Result<Record> edgeResult = dslContext.fetch(edgeSql, graphName);
            
            // 组合结果
            List<Map<String, Object>> data = new ArrayList<>();
            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("format", exportFormat);
            exportData.put("nodes", convertToMapList(nodeResult));
            exportData.put("edges", convertToMapList(edgeResult));
            data.add(exportData);
            
            return new DataRecord("export_graph_json", "exported " + graphName + " as " + exportFormat, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("导出图数据失败", e);
            throw new RuntimeException("导出图数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord importGraphFromJson(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        JsonNode jsonData = rootNode.get("json_data");
        String mergeStrategy = rootNode.has("merge_strategy") ? rootNode.get("merge_strategy").asText() : "create";
        int batchSize = rootNode.has("batch_size") ? rootNode.get("batch_size").asInt() : 1000;
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(importGraphFromJsonLogic(configuration.dsl(), graphName, jsonData, mergeStrategy, batchSize));
            });
        } else {
            result.set(importGraphFromJsonLogic(dslContext, graphName, jsonData, mergeStrategy, batchSize));
        }
        
        return result.get();
    }

    private DataRecord importGraphFromJsonLogic(DSLContext dslContext, String graphName, JsonNode jsonData, String mergeStrategy, int batchSize) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            int nodesImported = 0;
            int edgesImported = 0;
            
            // 导入节点
            if (jsonData.has("nodes")) {
                JsonNode nodes = jsonData.get("nodes");
                nodesImported = importNodes(dslContext, graphName, nodes, mergeStrategy, batchSize);
            }
            
            // 导入边
            if (jsonData.has("edges")) {
                JsonNode edges = jsonData.get("edges");
                edgesImported = importEdges(dslContext, graphName, edges, mergeStrategy, batchSize);
            }
            
            String summary = String.format("imported %d nodes and %d edges", nodesImported, edgesImported);
            return new DataRecord("import_graph_json", summary, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("导入图数据失败", e);
            throw new RuntimeException("导入图数据失败: " + e.getMessage(), e);
        }
    }

    // =============== 图与向量数据混合操作实现 ===============

    @Override
    public DataRecord addNodeEmbeddings(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String embeddingProperty = rootNode.get("embedding_property").asText();
        int vectorDimension = rootNode.get("vector_dimension").asInt();
        JsonNode embeddingData = rootNode.get("embedding_data");
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(addNodeEmbeddingsLogic(configuration.dsl(), graphName, nodeLabel, embeddingProperty, vectorDimension, embeddingData));
            });
        } else {
            result.set(addNodeEmbeddingsLogic(dslContext, graphName, nodeLabel, embeddingProperty, vectorDimension, embeddingData));
        }
        
        return result.get();
    }

    private DataRecord addNodeEmbeddingsLogic(DSLContext dslContext, String graphName, String nodeLabel, String embeddingProperty, int vectorDimension, JsonNode embeddingData) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            int updatedNodes = 0;
            
            for (JsonNode embedding : embeddingData) {
                String nodeId = embedding.get("node_id").asText();
                JsonNode vector = embedding.get("vector");
                
                // 构建向量字符串
                StringBuilder vectorStr = new StringBuilder("[");
                for (int i = 0; i < vector.size(); i++) {
                    if (i > 0) vectorStr.append(",");
                    vectorStr.append(vector.get(i).asDouble());
                }
                vectorStr.append("]");
                
                String cypherQuery = String.format(
                    "MATCH (n:%s) WHERE id(n) = %s SET n.%s = %s::vector RETURN n",
                    nodeLabel, nodeId, embeddingProperty, vectorStr.toString()
                );
                
                String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype)";
                Result<Record> result = dslContext.fetch(sql, graphName);
                
                if (!result.isEmpty()) {
                    updatedNodes++;
                }
            }
            
            String summary = String.format("added embeddings to %d nodes", updatedNodes);
            return new DataRecord("add_node_embeddings", summary, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("添加节点嵌入失败", e);
            throw new RuntimeException("添加节点嵌入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord findSimilarNodes(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String embeddingProperty = rootNode.get("embedding_property").asText();
        JsonNode queryVector = rootNode.get("query_vector");
        String similarityMetric = rootNode.has("similarity_metric") ? rootNode.get("similarity_metric").asText() : "cosine";
        int limit = rootNode.has("limit") ? rootNode.get("limit").asInt() : 10;
        double threshold = rootNode.has("threshold") ? rootNode.get("threshold").asDouble() : 0.8;
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 构建查询向量字符串
            StringBuilder vectorStr = new StringBuilder("[");
            for (int i = 0; i < queryVector.size(); i++) {
                if (i > 0) vectorStr.append(",");
                vectorStr.append(queryVector.get(i).asDouble());
            }
            vectorStr.append("]");
            
            String operator = getSimilarityOperator(similarityMetric);
            
            String cypherQuery = String.format(
                "MATCH (n:%s) WHERE n.%s IS NOT NULL " +
                "WITH n, (n.%s %s '%s'::vector) as similarity " +
                "WHERE similarity >= %f " +
                "RETURN n, similarity " +
                "ORDER BY similarity DESC " +
                "LIMIT %d",
                nodeLabel, embeddingProperty, embeddingProperty, operator, vectorStr.toString(), threshold, limit
            );
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype, similarity agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("find_similar_nodes", "similar nodes using " + similarityMetric, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查找相似节点失败", e);
            throw new RuntimeException("查找相似节点失败: " + e.getMessage(), e);
        }
    }

    // =============== 图与JSON数据混合操作实现 ===============

    @Override
    public DataRecord queryNodesWithJson(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        String jsonProperty = rootNode.get("json_property").asText();
        String jsonPath = rootNode.get("json_path").asText();
        String jsonValue = rootNode.has("json_value") ? rootNode.get("json_value").asText() : "";
        String jsonOperator = rootNode.get("json_operator").asText();
        
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            String whereClause = buildJsonWhereClause(jsonProperty, jsonPath, jsonValue, jsonOperator);
            
            String cypherQuery = String.format(
                "MATCH (n:%s) WHERE %s RETURN n, n.%s as json_data",
                nodeLabel, whereClause, jsonProperty
            );
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype, json_data agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            List<Map<String, Object>> data = convertToMapList(result);
            
            return new DataRecord("query_nodes_with_json", "nodes with JSON " + jsonOperator + " " + jsonPath, Optional.of(data), Optional.empty());
        } catch (Exception e) {
            log.error("查询JSON节点失败", e);
            throw new RuntimeException("查询JSON节点失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataRecord updateNodeJson(DSLContext dslContext, JsonNode rootNode) {
        String graphName = rootNode.get("graph_name").asText();
        String nodeLabel = rootNode.get("node_label").asText();
        JsonNode nodeFilter = rootNode.get("node_filter");
        String jsonProperty = rootNode.get("json_property").asText();
        JsonNode jsonUpdates = rootNode.get("json_updates");
        boolean useTransaction = rootNode.has("use_transaction") && rootNode.get("use_transaction").asBoolean();
        
        AtomicReference<DataRecord> result = new AtomicReference<>();
        
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result.set(updateNodeJsonLogic(configuration.dsl(), graphName, nodeLabel, nodeFilter, jsonProperty, jsonUpdates));
            });
        } else {
            result.set(updateNodeJsonLogic(dslContext, graphName, nodeLabel, nodeFilter, jsonProperty, jsonUpdates));
        }
        
        return result.get();
    }

    private DataRecord updateNodeJsonLogic(DSLContext dslContext, String graphName, String nodeLabel, JsonNode nodeFilter, String jsonProperty, JsonNode jsonUpdates) {
        try {
            dslContext.execute("LOAD 'age'");
            dslContext.execute("SET search_path TO ag_catalog");
            
            // 构建节点过滤条件
            StringBuilder filterClause = new StringBuilder();
            nodeFilter.fields().forEachRemaining(entry -> {
                if (filterClause.length() > 0) filterClause.append(" AND ");
                filterClause.append("n.").append(entry.getKey()).append(" = '").append(entry.getValue().asText()).append("'");
            });
            
            int updatedNodes = 0;
            
            for (JsonNode update : jsonUpdates) {
                String path = update.get("path").asText();
                String value = update.get("value").asText();
                String operation = update.get("operation").asText();
                
                String setClause = buildJsonUpdateClause(jsonProperty, path, value, operation);
                
                String cypherQuery = String.format(
                    "MATCH (n:%s) WHERE %s SET %s RETURN n",
                    nodeLabel, filterClause.toString(), setClause
                );
                
                String sql = "SELECT * FROM cypher(?, $$" + cypherQuery + "$$) as (node agtype)";
                Result<Record> result = dslContext.fetch(sql, graphName);
                
                updatedNodes += result.size();
            }
            
            String summary = String.format("updated JSON properties in %d nodes", updatedNodes);
            return new DataRecord("update_node_json", summary, Optional.empty(), Optional.empty());
        } catch (Exception e) {
            log.error("更新节点JSON失败", e);
            throw new RuntimeException("更新节点JSON失败: " + e.getMessage(), e);
        }
    }

    // =============== 新增辅助方法 ===============

    private String buildNodeDegreeQuery(String nodeLabel, JsonNode nodeProperties, String degreeType, String edgeLabel) {
        StringBuilder query = new StringBuilder("MATCH (n:").append(nodeLabel).append(")");
        
        // 添加节点属性过滤
        if (nodeProperties != null && nodeProperties.size() > 0) {
            query.append(" WHERE ");
            nodeProperties.fields().forEachRemaining(entry -> {
                query.append("n.").append(entry.getKey()).append(" = '").append(entry.getValue().asText()).append("' AND ");
            });
            query.setLength(query.length() - 5); // 移除最后的 " AND "
        }
        
        String edgePattern = edgeLabel.isEmpty() ? "" : ":" + edgeLabel;
        
        switch (degreeType.toLowerCase()) {
            case "in" -> query.append(" OPTIONAL MATCH (n)<-[").append(edgePattern).append("]-(m) RETURN n, count(m) as degree");
            case "out" -> query.append(" OPTIONAL MATCH (n)-[").append(edgePattern).append("]->(m) RETURN n, count(m) as degree");
            default -> query.append(" OPTIONAL MATCH (n)-[").append(edgePattern).append("]-(m) RETURN n, count(m) as degree");
        }
        
        return query.toString();
    }

    private String buildCentralityQuery(String centralityType, String nodeLabel, String edgeLabel, int limit) {
        String edgePattern = edgeLabel.isEmpty() ? "" : ":" + edgeLabel;
        
        return switch (centralityType.toLowerCase()) {
            case "degree" -> String.format(
                "MATCH (n:%s) OPTIONAL MATCH (n)-[%s]-(m) " +
                "WITH n, count(m) as degree " +
                "RETURN n, degree as centrality " +
                "ORDER BY centrality DESC LIMIT %d",
                nodeLabel, edgePattern, limit
            );
            case "closeness" -> String.format(
                "MATCH (n:%s) " +
                "MATCH (n)-[%s*]-(m:%s) " +
                "WITH n, avg(length(shortestPath((n)-[%s*]-(m)))) as avgDistance " +
                "RETURN n, 1.0/avgDistance as centrality " +
                "ORDER BY centrality DESC LIMIT %d",
                nodeLabel, edgePattern, nodeLabel, edgePattern, limit
            );
            case "betweenness" -> String.format(
                "MATCH (a:%s), (b:%s) WHERE a <> b " +
                "MATCH path = shortestPath((a)-[%s*]-(b)) " +
                "WITH nodes(path) as pathNodes " +
                "UNWIND pathNodes as n " +
                "RETURN n, count(*) as centrality " +
                "ORDER BY centrality DESC LIMIT %d",
                nodeLabel, nodeLabel, edgePattern, limit
            );
            default -> throw new IllegalArgumentException("Unsupported centrality type: " + centralityType);
        };
    }

    private String buildSubgraphQuery(JsonNode centerNode, int maxDepth, JsonNode edgeLabels, boolean includeProperties) {
        StringBuilder query = new StringBuilder("MATCH (center");
        
        if (centerNode.has("label")) {
            query.append(":").append(centerNode.get("label").asText());
        }
        
        if (centerNode.has("properties")) {
            query.append(" ").append(centerNode.get("properties").toString());
        }
        
        query.append(") MATCH (center)-[");
        
        if (edgeLabels != null && edgeLabels.isArray()) {
            query.append(":");
            for (int i = 0; i < edgeLabels.size(); i++) {
                if (i > 0) query.append("|");
                query.append(edgeLabels.get(i).asText());
            }
        }
        
        query.append("*1..").append(maxDepth).append("]-(connected) ");
        
        if (includeProperties) {
            query.append("RETURN center, connected, relationships((center)-[*1..").append(maxDepth).append("]-(connected)) as edges");
        } else {
            query.append("RETURN center, connected");
        }
        
        return query.toString();
    }

    private String buildAggregationQuery(String aggregationType, String groupBy, JsonNode filters) {
        StringBuilder query = new StringBuilder();
        
        switch (aggregationType.toLowerCase()) {
            case "node_count" -> {
                query.append("MATCH (n");
                if (filters != null && filters.has("node_label")) {
                    query.append(":").append(filters.get("node_label").asText());
                }
                query.append(") ");
                if (!groupBy.isEmpty()) {
                    query.append("RETURN labels(n) as ").append(groupBy).append(", count(n) as count");
                } else {
                    query.append("RETURN count(n) as node_count");
                }
            }
            case "edge_count" -> {
                query.append("MATCH ()-[r");
                if (filters != null && filters.has("edge_label")) {
                    query.append(":").append(filters.get("edge_label").asText());
                }
                query.append("]->() ");
                if (!groupBy.isEmpty()) {
                    query.append("RETURN type(r) as ").append(groupBy).append(", count(r) as count");
                } else {
                    query.append("RETURN count(r) as edge_count");
                }
            }
            case "avg_degree" -> {
                query.append("MATCH (n");
                if (filters != null && filters.has("node_label")) {
                    query.append(":").append(filters.get("node_label").asText());
                }
                query.append(") OPTIONAL MATCH (n)-[]-(m) ");
                query.append("WITH n, count(m) as degree ");
                query.append("RETURN avg(degree) as avg_degree");
            }
            case "density" -> {
                query.append("MATCH (n) OPTIONAL MATCH ()-[r]->() ");
                query.append("WITH count(DISTINCT n) as nodeCount, count(r) as edgeCount ");
                query.append("RETURN toFloat(edgeCount) / (nodeCount * (nodeCount - 1)) as density");
            }
            default -> throw new IllegalArgumentException("Unsupported aggregation type: " + aggregationType);
        }
        
        return query.toString();
    }

    private String buildExportNodesQuery(JsonNode filters, boolean includeProperties) {
        StringBuilder query = new StringBuilder("MATCH (n");
        
        if (filters != null && filters.has("node_labels")) {
            JsonNode nodeLabels = filters.get("node_labels");
            if (nodeLabels.isArray() && nodeLabels.size() > 0) {
                query.append(":");
                for (int i = 0; i < nodeLabels.size(); i++) {
                    if (i > 0) query.append("|");
                    query.append(nodeLabels.get(i).asText());
                }
            }
        }
        
        query.append(") RETURN ");
        
        if (includeProperties) {
            query.append("id(n) as id, labels(n) as labels, properties(n) as properties");
        } else {
            query.append("id(n) as id, labels(n) as labels");
        }
        
        return query.toString();
    }

    private String buildExportEdgesQuery(JsonNode filters, boolean includeProperties) {
        StringBuilder query = new StringBuilder("MATCH (a)-[r");
        
        if (filters != null && filters.has("edge_labels")) {
            JsonNode edgeLabels = filters.get("edge_labels");
            if (edgeLabels.isArray() && edgeLabels.size() > 0) {
                query.append(":");
                for (int i = 0; i < edgeLabels.size(); i++) {
                    if (i > 0) query.append("|");
                    query.append(edgeLabels.get(i).asText());
                }
            }
        }
        
        query.append("]->(b) RETURN ");
        
        if (includeProperties) {
            query.append("id(a) as source, id(b) as target, type(r) as type, properties(r) as properties");
        } else {
            query.append("id(a) as source, id(b) as target, type(r) as type");
        }
        
        return query.toString();
    }

    private int importNodes(DSLContext dslContext, String graphName, JsonNode nodes, String mergeStrategy, int batchSize) {
        int imported = 0;
        List<JsonNode> batch = new ArrayList<>();
        
        for (JsonNode node : nodes) {
            batch.add(node);
            
            if (batch.size() >= batchSize) {
                imported += processBatchNodes(dslContext, graphName, batch, mergeStrategy);
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            imported += processBatchNodes(dslContext, graphName, batch, mergeStrategy);
        }
        
        return imported;
    }

    private int importEdges(DSLContext dslContext, String graphName, JsonNode edges, String mergeStrategy, int batchSize) {
        int imported = 0;
        List<JsonNode> batch = new ArrayList<>();
        
        for (JsonNode edge : edges) {
            batch.add(edge);
            
            if (batch.size() >= batchSize) {
                imported += processBatchEdges(dslContext, graphName, batch, mergeStrategy);
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            imported += processBatchEdges(dslContext, graphName, batch, mergeStrategy);
        }
        
        return imported;
    }

    private int processBatchNodes(DSLContext dslContext, String graphName, List<JsonNode> nodes, String mergeStrategy) {
        int processed = 0;
        
        for (JsonNode node : nodes) {
            String operation = "merge".equals(mergeStrategy) ? "MERGE" : "CREATE";
            
            StringBuilder cypherQuery = new StringBuilder(operation).append(" (n");
            
            if (node.has("labels")) {
                JsonNode labels = node.get("labels");
                for (JsonNode label : labels) {
                    cypherQuery.append(":").append(label.asText());
                }
            }
            
            if (node.has("properties")) {
                cypherQuery.append(" ").append(node.get("properties").toString());
            }
            
            cypherQuery.append(") RETURN n");
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery.toString() + "$$) as (node agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            
            if (!result.isEmpty()) {
                processed++;
            }
        }
        
        return processed;
    }

    private int processBatchEdges(DSLContext dslContext, String graphName, List<JsonNode> edges, String mergeStrategy) {
        int processed = 0;
        
        for (JsonNode edge : edges) {
            String operation = "merge".equals(mergeStrategy) ? "MERGE" : "CREATE";
            
            StringBuilder cypherQuery = new StringBuilder("MATCH (a), (b) WHERE id(a) = ");
            cypherQuery.append(edge.get("source").asText());
            cypherQuery.append(" AND id(b) = ").append(edge.get("target").asText());
            cypherQuery.append(" ").append(operation).append(" (a)-[r:").append(edge.get("type").asText());
            
            if (edge.has("properties")) {
                cypherQuery.append(" ").append(edge.get("properties").toString());
            }
            
            cypherQuery.append("]->(b) RETURN r");
            
            String sql = "SELECT * FROM cypher(?, $$" + cypherQuery.toString() + "$$) as (edge agtype)";
            Result<Record> result = dslContext.fetch(sql, graphName);
            
            if (!result.isEmpty()) {
                processed++;
            }
        }
        
        return processed;
    }

    private String getSimilarityOperator(String metric) {
        return switch (metric.toLowerCase()) {
            case "cosine" -> "<=>";
            case "l2" -> "<->";
            case "inner_product" -> "<#>";
            default -> "<=>";
        };
    }

    private String buildJsonWhereClause(String jsonProperty, String jsonPath, String jsonValue, String jsonOperator) {
        return switch (jsonOperator.toLowerCase()) {
            case "contains" -> String.format("n.%s #> '%s' @> '\"%s\"'", jsonProperty, jsonPath, jsonValue);
            case "equals" -> String.format("n.%s #> '%s' = '\"%s\"'", jsonProperty, jsonPath, jsonValue);
            case "exists" -> String.format("n.%s #> '%s' IS NOT NULL", jsonProperty, jsonPath);
            default -> throw new IllegalArgumentException("Unsupported JSON operator: " + jsonOperator);
        };
    }

    private String buildJsonUpdateClause(String jsonProperty, String path, String value, String operation) {
        return switch (operation.toLowerCase()) {
            case "set" -> String.format("n.%s = jsonb_set(n.%s, '%s', '\"%s\"')", jsonProperty, jsonProperty, path, value);
            case "append" -> String.format("n.%s = jsonb_set(n.%s, '%s', (n.%s #> '%s') || '\"%s\"')", jsonProperty, jsonProperty, path, jsonProperty, path, value);
            case "remove" -> String.format("n.%s = n.%s #- '%s'", jsonProperty, jsonProperty, path);
            default -> throw new IllegalArgumentException("Unsupported JSON operation: " + operation);
        };
    }
}