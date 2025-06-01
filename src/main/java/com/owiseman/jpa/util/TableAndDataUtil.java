package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.owiseman.jpa.model.DataRecord;
import com.owiseman.jpa.model.PaginationInfo;
import lombok.extern.log4j.Log4j;

import org.jooq.Condition;
import org.jooq.ConstraintForeignKeyOnStep;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * @author acproject@qq.com
 * @date 2025-01-18 19:19
 */
@Log4j
public class TableAndDataUtil implements TabaleAndDataOperation {
    private static final String DATA_SYNC_TOPIC = "data-sync-topic";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static volatile TableAndDataUtil instance;

    private TableAndDataUtil() {
    }

    public static TableAndDataUtil getInstance() {
        if (instance == null) {
            synchronized (TableAndDataUtil.class) {
                if (instance == null) {
                    instance = new TableAndDataUtil();
                }
            }
        }
        return instance;
    }

    public static void processRequest(DSLContext dslContext,
                                      RabbitTemplate rabbitTemplate, String json) throws Exception {
        processRequest(dslContext, json);
        getInstance().sendToMQ(rabbitTemplate, json);
    }

    public static DataRecord processRequest(DSLContext dslContext,
                                            String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        String operation = rootNode.get("operation").asText();
        switch (operation) {
            case "create_table" -> {
                return getInstance().createTable(dslContext, rootNode);
            }
            case "create_batch_table" -> {
                return getInstance().createBatchTable(dslContext, rootNode);
            }
            case "drop_table" -> {
                return getInstance().dropTable(dslContext, rootNode);
            }
            case "alter_table" -> {
                return getInstance().alterTable(dslContext, rootNode);
            }
            case "insert" -> {
                return getInstance().insertData(dslContext, rootNode);
            }
            case "insert_batch" -> {
                return getInstance().insertBatchData(dslContext, rootNode);
            }
            case "update_data" -> {
                return getInstance().updateData(dslContext, rootNode);
            }
            case "update_batch" -> {
                return getInstance().updateBatchData(dslContext, rootNode);
            }
            case "delete" -> {
                return getInstance().deleteData(dslContext, rootNode);
            }
            case "select" -> {
                return getInstance().selectData(dslContext, rootNode);
            }
            case "select_batch" -> {
                return getInstance().selectJoinData(dslContext, rootNode);
            }

            case "add_foreign" -> {
                return getInstance().addForeignKey(dslContext, rootNode);
            }

            case "create_index" -> {
                return getInstance().createIndex(dslContext, rootNode);
            }

            case "drop_index" -> {
                return getInstance().dropIndex(dslContext, rootNode);
            }
            
            // =============== Apache AGE 图数据库操作 ===============
            case "create_graph" -> {
                return GraphDatabaseUtil.getInstance().createGraph(dslContext, rootNode);
            }
            case "drop_graph" -> {
                return GraphDatabaseUtil.getInstance().dropGraph(dslContext, rootNode);
            }
            case "graph_stats" -> {
                return GraphDatabaseUtil.getInstance().getGraphStats(dslContext, rootNode);
            }
            case "create_vertex_label" -> {
                return GraphDatabaseUtil.getInstance().createVertexLabel(dslContext, rootNode);
            }
            case "create_edge_label" -> {
                return GraphDatabaseUtil.getInstance().createEdgeLabel(dslContext, rootNode);
            }
            case "cypher_create" -> {
                return GraphDatabaseUtil.getInstance().cypherCreate(dslContext, rootNode);
            }
            case "cypher_match" -> {
                return GraphDatabaseUtil.getInstance().cypherMatch(dslContext, rootNode);
            }
            case "cypher_merge" -> {
                return GraphDatabaseUtil.getInstance().cypherMerge(dslContext, rootNode);
            }
            case "cypher_delete" -> {
                return GraphDatabaseUtil.getInstance().cypherDelete(dslContext, rootNode);
            }
            case "cypher_set" -> {
                return GraphDatabaseUtil.getInstance().cypherSet(dslContext, rootNode);
            }
            case "batch_create_nodes" -> {
                return GraphDatabaseUtil.getInstance().batchCreateNodes(dslContext, rootNode);
            }
            case "batch_create_edges" -> {
                return GraphDatabaseUtil.getInstance().batchCreateEdges(dslContext, rootNode);
            }
            case "load_vertices_from_file" -> {
                return GraphDatabaseUtil.getInstance().loadVerticesFromFile(dslContext, rootNode);
            }
            case "load_edges_from_file" -> {
                return GraphDatabaseUtil.getInstance().loadEdgesFromFile(dslContext, rootNode);
            }
            case "shortest_path" -> {
                return GraphDatabaseUtil.getInstance().findShortestPath(dslContext, rootNode);
            }
            case "all_paths" -> {
                return GraphDatabaseUtil.getInstance().findAllPaths(dslContext, rootNode);
            }
            case "execute_cypher" -> {
                return GraphDatabaseUtil.getInstance().executeCypher(dslContext, rootNode);
            }
            
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    /**
     * 发送消息到 MQ
     *
     * @param rabbitTemplate
     * @param json
     */
    private void sendToMQ(RabbitTemplate rabbitTemplate, String json) {
        rabbitTemplate.convertAndSend(DATA_SYNC_TOPIC, json);
        System.out.println("Send to MQ: " + json); // 输出到控制台，不记录到日志里
    }

    @Override
    public DataRecord createBatchTable(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(createBatchTableLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(createBatchTableLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    private DataRecord createBatchTableLogic(DSLContext dslContext, JsonNode rootNode) {
        JsonNode dataArray = rootNode.get("data");
        List<DataRecord> result = new ArrayList<>();
        for (JsonNode dataNode : dataArray) {
            result.add(createTable(dslContext, dataNode));
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[ ");
        for (int i = 0; i < result.size(); i++) {
            if (i == result.size() - 1) {
                stringBuilder.append(result.get(i).name());
            } else {
                stringBuilder.append(result.get(i).name()).append(",");
            }
        }

        String names = stringBuilder.append(" ]").toString();

        return new DataRecord("create batch table", names, Optional.empty(), Optional.empty());

    }

    public DataRecord createTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode columns = rootNode.get("columns");
        JsonNode primaryKeysNode = rootNode.get("primary_keys");
        JsonNode uniqueKeysNode = rootNode.get("unique_keys");
        JsonNode foreignKeysNode = rootNode.get("foreign_keys");

        var createTableStep = dslContext.createTableIfNotExists(tableName);

        for (JsonNode column : columns) {
            String columnName = column.get("name").asText();
            String columnType = column.get("type").asText();
            var dataType = getSqlDataType(columnType);
            if (column.has("length")) {
                int length = column.get("length").asInt();
                dataType = dataType.length(length);
            }

            if (column.has("auto_increment") && column.get("auto_increment").asBoolean()) {
                dataType = dataType.identity(true);
            }

            if (column.has("uuid") && column.get("uuid").asBoolean()) {
                dataType = dataType.defaultValue(DSL.uuid());
            }

            if (column.has("null")) {
                boolean nullable = column.get("null").asBoolean();
                if (nullable) {
                    dataType.nullable(true);
                    dataType = dataType.null_();
                } else {
                    dataType.nullable(false);
                    dataType = dataType.notNull();
                }
            }
            if (column.has("default")) {
                String defaultValue = column.get("default").asText();
                if (isNumber(defaultValue)) {
                    if (isInteger(defaultValue)) {
                        dataType = dataType.defaultValue(DSL.val(defaultValue));
                    } else if (isFloat(defaultValue)) {
                        dataType = dataType.defaultValue(DSL.val(defaultValue));
                    }
                } else {
                    dataType = dataType.defaultValue(DSL.val(defaultValue));
                }

            }
            createTableStep.column(columnName, dataType);
        }

        List<String> primaryKeys = new ArrayList<>();

        if (primaryKeysNode != null && primaryKeysNode.isArray()) {

            for (JsonNode pk : primaryKeysNode) {
                primaryKeys.add(pk.asText());
            }

        } else {
            for (JsonNode column : columns) {
                if (column.has("primary_key") && column.get("primary_key").asBoolean()) {
                    primaryKeys.add(column.get("name").asText());
                }
            }
        }

        if (!primaryKeys.isEmpty()) {
            createTableStep.constraint(DSL.constraint("pk_" + tableName)
                    .primaryKey(getAllPrimaryKeys(primaryKeys)));
        }

        if (uniqueKeysNode != null && uniqueKeysNode.isArray()) {
            for (JsonNode uniqueKeyNode : uniqueKeysNode) {
                String uniqueKeyName = uniqueKeyNode.get("name").asText();
                List<String> uniqueColumns = new ArrayList<>();
                for (JsonNode columnNode : uniqueKeyNode.get("columns")) {
                    uniqueColumns.add(columnNode.asText());
                }
                createTableStep.constraint(
                        DSL.constraint(uniqueKeyName).unique(getAllPrimaryKeys(uniqueColumns)));
            }
        }

        if (foreignKeysNode != null && foreignKeysNode.isArray()) {
            for (JsonNode fkNode : foreignKeysNode) {
                // 1. 解析外键配置
                List<String> columnList = new ArrayList<>();
                for (JsonNode colNode : fkNode.get("columns")) {
                    columnList.add(colNode.asText());
                }

                String refTable = fkNode.get("referenced_table").asText();

                List<String> refColumns = new ArrayList<>();
                for (JsonNode refColNode : fkNode.get("referenced_columns")) {
                    refColumns.add(refColNode.asText());
                }

                // 2. 生成约束名称（格式：fk_本表名_外键字段名）
                String fkName = "fk_" + tableName + "_" + String.join("_", columnList);


                var constraint = DSL.constraint(fkName)
                        .foreignKey(columnList.stream()
                                .map(col -> DSL.field(DSL.name(col)))
                                .toArray(Field[]::new))
                        .references(DSL.table(refTable),
                                refColumns.stream()
                                        .map(refCol -> DSL.field(DSL.name(refCol)))
                                        .toArray(Field[]::new));

                // 处理级联操作
                if (fkNode.has("on_delete")) {

                    constraint = getReferentialAction(constraint,
                            fkNode.asText(), fkNode.get("on_delete").asText());
                }

                if (fkNode.has("on_update")) {
                    constraint = getReferentialAction(constraint,
                            fkNode.asText(), fkNode.get("on_update").asText());
                }
                // 3. 创建外键约束
                createTableStep.constraint(
                        constraint
                );
            }
        }

        createTableStep.execute();
        DataRecord dataRecord = new DataRecord("create table", tableName, null, null);
        log.info("Create table: " + tableName);
        return dataRecord;
    }

    private static DataType getSqlDataType(String typeName) {
        return switch (typeName) {
            case "int", "java.lang.Integer", "INTEGER" -> SQLDataType.INTEGER;
            case "long", "java.lang.Long", "BIGINT" -> SQLDataType.BIGINT;
            case "String", "java.lang.String", "VARCHAR" -> SQLDataType.VARCHAR;
            case "LocalDate", "LOCALDATE", "java.time.LocalDate" -> SQLDataType.LOCALDATE;
            case "LocalDateTime", "java.time.LocalDateTime" -> SQLDataType.LOCALDATETIME;
            case "LocalTime", "LOCALTIME", "java.time.LocalTime" -> SQLDataType.LOCALTIME;
            case "OffsetDateTime", "java.time.OffsetDateTime", "java.util.Date" -> SQLDataType.DATE;
            case "JSONB", "org.jooq.JSONB" -> SQLDataType.JSONB;
            case "Boolean", "java.lang.Boolean" -> SQLDataType.BOOLEAN;
            case "JSON", "org.jooq.JSON" -> SQLDataType.JSON;
            case "UUID" -> SQLDataType.UUID;
            case "GEOGRAPHY" -> SQLDataType.GEOGRAPHY;
            case "GEOMETRY" -> SQLDataType.GEOMETRY;
            default -> SQLDataType.OTHER;
        };
    }

    @Override
    public DataRecord dropTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        dslContext.execute("DROP TABLE IF EXISTS " + tableName);
        log.info("Drop table: " + tableName);
        return new DataRecord("drop table", tableName, null, null);
    }

    @Override
    public DataRecord alterTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode columns = rootNode.get("columns");

        for (JsonNode column : columns) {
            String columnName = column.get("name").asText();
            String operation = column.has("operation") ? column.get("operation").asText() : "add";
            String columnType = column.get("type").asText();
            var dataType = getSqlDataType(columnType);
            var alterColumn = dslContext.alterTable(tableName).alterColumn(columnName);
            switch (operation.toLowerCase()) {
                case "add" -> {
                    if (columnType == null)
                        throw new IllegalArgumentException("Column type is required for add opertion");
                    if (column.has("length")) {
                        int length = column.get("length").asInt();
                        dataType = dataType.length(length);
                    }

                    if (column.has("auto_increment") && column.get("auto_increment").asBoolean()) {
                        dataType = dataType.identity(true);
                    }

                    if (column.has("uuid") && column.get("uuid").asBoolean()) {
                        dataType = dataType.defaultValue(DSL.uuid());
                    }

                    if (column.has("null")) {
                        boolean nullable = column.get("null").asBoolean();
                        if (nullable) {
                            dataType.nullable(true);
                            dataType = dataType.null_();
                        } else {
                            dataType.nullable(false);
                            dataType = dataType.notNull();
                        }
                    }
                    if (column.has("default")) {
                        String defaultValue = column.get("default").asText();
                        if (isNumber(defaultValue)) {
                            if (isInteger(defaultValue)) {
                                dataType = dataType.defaultValue(DSL.val(defaultValue));
                            } else if (isFloat(defaultValue)) {
                                dataType = dataType.defaultValue(DSL.val(defaultValue));
                            }
                        } else {
                            dataType = dataType.defaultValue(DSL.val(defaultValue));
                        }

                    }
                    dslContext.alterTable(tableName).addColumn(columnName, dataType).execute();
                }
                case "set_default" -> {
                    if (columnType == null)
                        throw new IllegalArgumentException("Column type is required for modify opertion");
                    if (column.has("default")) {
                        String defaultValue = column.get("default").asText();
                        alterColumn.setDefault(DSL.val(defaultValue)).execute();
                    }
                }
                case "modify" -> {
                    if (columnType == null)
                        throw new IllegalArgumentException("Column type is required for modify opertion");
                    if (column.has("null")) {
                        boolean nullable = column.get("null").asBoolean();
                        if (nullable) {
                            dataType.nullable(true);
                            dataType = dataType.null_();
                        } else {
                            dataType.nullable(false);
                            dataType = dataType.notNull();
                        }
                    }
                    if (column.has("length")) {
                        int length = column.get("length").asInt();
                        dataType = dataType.length(length);
                    }

                    if (column.has("auto_increment") && column.get("auto_increment").asBoolean()) {
                        if (column.has("primary_key") && column.get("primary_key").asBoolean()) {
                            modifyPrimaryKeyToAutoIncrement(dslContext, tableName, column.get("name").asText());
                        }
                    }

                    if (column.has("uuid") && column.get("uuid").asBoolean()) {
                        dataType = dataType.defaultValue(DSL.uuid());
                    }

                    alterColumn.set(dataType).execute();
                }
                case "drop" -> dslContext.alterTable(tableName).dropColumn(columnName).execute();
                default -> throw new IllegalArgumentException("Column type is required for add opertion");
            }
        }
        log.info("Table altered: " + tableName);
        return new DataRecord("alter table", tableName, null, null);
    }

    @Override
    public DataRecord insertData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataNode = rootNode.get("data");
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();

        Map<String, Object> data = new LinkedHashMap<>();
        dataNode.fields().forEachRemaining(entry -> {
            var valueNode = entry.getValue().asText();
            if (isNumber(valueNode)) {
                if (isInteger(valueNode)) {
                    data.put(entry.getKey(), Integer.parseInt(valueNode));
                } else if (isFloat(valueNode)) {
                    data.put(entry.getKey(), Double.parseDouble(valueNode));
                }
            } else {
                data.put(entry.getKey(), valueNode);
            }

        });
        // use transaction
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                DSLContext txContext = DSL.using(configuration);
                txContext.insertInto(DSL.table(tableName))
                        .set(data)
                        .execute();
            });
        } else {
            var insertStep = dslContext.insertInto(DSL.table(tableName));
            var insertStepMore = insertStep.set(data);
            insertStepMore.execute();
        }

        log.info("Insert data into table: " + tableName);
        return new DataRecord("insert", tableName, null, null);
    }

    @Override
    public DataRecord insertBatchData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataArray = rootNode.get("data");
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        List<Map<String, Object>> dataList = new ArrayList<>();

        for (JsonNode dataNode : dataArray) {
            Map<String, Object> data = new LinkedHashMap<>();
            dataNode.fields().forEachRemaining(entry -> {
                var valueNode = entry.getValue().asText();
                if (isNumber(valueNode)) {
                    if (isInteger(valueNode)) {
                        data.put(entry.getKey(), Integer.parseInt(valueNode));
                    } else if (isFloat(valueNode)) {
                        data.put(entry.getKey(), Double.parseDouble(valueNode));
                    }
                } else {
                    data.put(entry.getKey(), valueNode);
                }
            });
            dataList.add(data);
        }

        if (useTransaction) {
            if (!dataList.isEmpty()) {
                dslContext.transaction(configuration -> {
                    DSLContext txContext = DSL.using(configuration);
                    InsertValuesStepN<Record> insertStep = txContext.insertInto(DSL.table(tableName))
                            .columns(dataList.getFirst().keySet().stream().map(DSL::field).collect(Collectors.toList()));
                    for (Map<String, Object> data : dataList) {
                        insertStep.values(data.values().toArray());
                    }
                    insertStep.execute();
                    log.info("Insert batch data into table: " + tableName);
                });
            }
        } else {
            if (!dataList.isEmpty()) {
                InsertValuesStepN<Record> insertStep = dslContext.insertInto(DSL.table(tableName))
                        .columns(dataList.getFirst().keySet().stream().map(DSL::field).collect(Collectors.toList()));

                for (Map<String, Object> data : dataList) {
                    insertStep.values(data.values().toArray());
                }
                insertStep.execute();
                log.info("Insert batch data into table: " + tableName);
            }
        }
        return new DataRecord("insert batch", tableName, null, null);
    }

    @Override
    public DataRecord updateData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataNode = rootNode.get("data");
        JsonNode whereArray = rootNode.get("where");

        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();

        Map<String, Object> data = new LinkedHashMap<>();
        dataNode.fields().forEachRemaining(entry ->
                data.put(entry.getKey(), entry.getValue().asText()));
        List<Condition> conditions = new ArrayList<>();
        Condition condition = DSL.noCondition();
        if (whereArray != null && whereArray.isArray()) {
            for (JsonNode whereNode : whereArray) {
                var operation = whereNode.get("operation").asText();
                var operationName = whereNode.get("name").asText();
                conditions.add(operatorCondition(operation, condition, whereNode, operationName));
            }
        }
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                DSLContext txContext = DSL.using(configuration);
                txContext.update(DSL.table(tableName))
                        .set(data)
                        .where(conditions)
                        .execute();
                log.info("Update data into table: " + tableName);
            });
        } else {
            dslContext.update(DSL.table(tableName))
                    .set(data)
                    .where(conditions)
                    .execute();
            log.info("Update data into table: " + tableName);
        }
        return new DataRecord("update", tableName, null, null);
    }

    @Override
    public DataRecord updateBatchData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataArray = rootNode.get("data");
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode dataNode : dataArray) {
            JsonNode valuesNode = dataNode.get("values");
            JsonNode whereArray = rootNode.get("where");

            Map<String, Object> data = new LinkedHashMap<>();
            valuesNode.fields().forEachRemaining(entry ->
                    data.put(entry.getKey(), entry.getValue().asText()));
            Condition condition = DSL.noCondition();
            if (whereArray != null && whereArray.isArray()) {
                for (JsonNode whereNode : whereArray) {
                    var operation = whereNode.get("operation").asText();
                    var operationName = whereNode.get("name").asText();
                    conditions.add(operatorCondition(operation, condition, whereNode, operationName));
                }
            }
            if (useTransaction) {
                dslContext.transaction(configuration -> {
                    DSLContext txContext = DSL.using(configuration);
                    txContext.update(DSL.table(tableName))
                            .set(data)
                            .where(condition).execute();

                });
            } else {
                dslContext.update(DSL.table(tableName))
                        .set(data)
                        .where(condition)
                        .execute();
            }
            log.info("Update batch data into table: " + tableName);
        }

        return new DataRecord("update batch", tableName, null, null);
    }

    @Override
    public DataRecord deleteData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();

        Condition condition = DSL.noCondition();
        whereNode.fields().forEachRemaining(entry ->
                condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                DSLContext txContext = DSL.using(configuration);
                txContext.deleteFrom(DSL.table(tableName))
                        .where(condition)
                        .execute();
            });
        } else {
            dslContext.deleteFrom(DSL.table(tableName))
                    .where(condition)
                    .execute();
        }
        log.info("Delete data from table: " + tableName);
        return new DataRecord("delete", tableName, null, null);
    }

    /**
     * Single-table query
     * usage example:
     * <p>
     * ```json
     * {
     * "table": "orders",
     * "where": {
     * "status": "completed"
     * },
     * "groupBy": ["customer_id"],
     * "having": {
     * "total_amount": "1000"
     * }
     * }
     * ```
     *
     * @param dslContext
     * @param rootNode
     */
    @Override
    public DataRecord selectData(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(selectDataLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(selectDataLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    @Override
    public DataRecord selectJoinData(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(selectJoinDataLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(selectJoinDataLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    public DataRecord selectDataLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");
        JsonNode groupByNode = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");
        JsonNode orderByNode = rootNode.get("orderBy");
        JsonNode paginationNode = rootNode.get("pagination");

        AtomicReference<Condition> condition = new AtomicReference<>(DSL.noCondition());
        // 构建WHERE条件子句
        if (whereNode != null) {
            whereNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode conditionNode = entry.getValue();
                if (conditionNode.isArray()) {
                    List<String> values = new ArrayList<>();
                    conditionNode.forEach(value -> values.add(value.asText()));
                  condition.set(condition.get().and(DSL.field(fieldName).in(values)));
                } else if (conditionNode.isObject()) {
                    String operator = conditionNode.get("operator").asText();
                    JsonNode valueNode = conditionNode.get("value");
                    condition.set(operatorCondition(operator, condition.get(), valueNode, fieldName));
                } else {
                    // default use eq condition
                    condition.set(condition.get().and(DSL.field(fieldName).eq(conditionNode.asText())));
                }
            });
        }
        // 构建Group by子句
        List<Field<?>> groupByFields = new ArrayList<>();
        if (groupByNode != null) {
            groupByNode.forEach(field -> groupByFields.add(DSL.field(field.asText())));
        }
        // 构件Having条件子句
        Condition havingCondition = DSL.noCondition();
        if (havingNode != null) {
            havingNode.fields().forEachRemaining(entry -> {
                havingCondition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText()));
            });
        }
        // 执行查询
        SelectConditionStep<Record> selectStep = dslContext.select()
                .from(DSL.table(tableName))
                .where(condition.get());

        if (!groupByFields.isEmpty()) {
            selectStep.groupBy(groupByFields);
        }

        if (havingCondition != DSL.noCondition()) {
            selectStep.having(havingCondition);
        }

        // process order by
        if (orderByNode != null) {
            String filedName = orderByNode.get("field").asText();
            String direction = orderByNode.get("direction").asText().toUpperCase(); // ASC or DESC

            Field<?> field = DSL.field(filedName);
            SortField<?> sortField = direction.equals("DESC") ? field.desc() : field.asc();
            selectStep.orderBy(sortField);
        }

        int page = 0;
        int pageSize = 0;
        int offset = 0;
        long totalItem = 0L;

        // process pagination
        if (paginationNode != null) {
            page = paginationNode.get("page").asInt();
            pageSize = paginationNode.get("pageSize").asInt();
            offset = (page - 1) * pageSize;
            selectStep.limit(pageSize).offset(offset);
        }
        totalItem = DataAnalysisUtil.count(dslContext, tableName);
        Result<Record> result = selectStep.fetch();
        var resultList = convertToMapList(result);
        PaginationInfo paginationInfo = new PaginationInfo(page, pageSize,
                totalItem, Math.toIntExact((totalItem + pageSize - 1) / pageSize));
        return new DataRecord("select", tableName,
                Optional.of(resultList), Optional.of(paginationInfo));
    }

    public List<Map<String, Object>> convertToMapList(Result<Record> result) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (Record record : result) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Field<?> field : record.fields()) {
                map.put(field.getName(), record.getValue(field));
            }
            mapList.add(map);
        }
        return mapList;
    }

    /**
     * Joint query of multiple tables
     * <p>
     * usage example:
     * ```json
     * {
     * "table": "orders",
     * "join": [
     * {
     * "type": "INNER",
     * "table": "customers",
     * "on": "orders.customer_id = customers.id"
     * }
     * ],
     * "where": {
     * "status": ["completed", "shipped"],
     * "price": {
     * "operator": "between",
     * "value": [100, 200]
     * },
     * "customers.country": {
     * "operator": "eq",
     * "value": "USA"
     * }
     * },
     * "groupBy": ["customers.id", "orders.status"],
     * "having": {
     * "COUNT(orders.id)": {
     * "operator": "gte",
     * "value": "5"
     * }
     * },
     * "orderByArr": [
     * {
     * "field": "price",
     * "direction": "DESC"
     * },
     * {
     * "field": "order_date",
     * "direction": "ASC"
     * }
     * ],
     * "pagination": {
     * "page": 2,
     * "pageSize": 10
     * }
     * }
     * If you have the same field name in multiple tables, we recommend that
     * you qualify the field with a table name or alias, such as orders.status
     * ```
     *
     * @param dslContext
     * @param rootNode
     */
    public DataRecord selectJoinDataLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereArray = rootNode.get("where");
        JsonNode joinArray = rootNode.get("join");
        JsonNode groupByArray = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");
        JsonNode orderByArray = rootNode.get("orderByArr");
        JsonNode paginationNode = rootNode.get("pagination");

         AtomicReference<Condition> condition = new AtomicReference<>(DSL.noCondition());

        // Process WHERE conditions
        if (whereArray != null) {
            whereArray.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode conditionNode = entry.getValue();

                if (conditionNode.isArray()) {
                    // Process `IN` condition
                    List<String> values = new ArrayList<>();
                    entry.getValue().forEach(value -> {
                        values.add(value.asText());
                       condition.set(condition.get().and(DSL.field(entry.getKey()).in(values)));
                    });
                } else if (conditionNode.isObject()) {
                   condition.set(operatorCondition(conditionNode.get("operator").asText(),
                            condition.get(), conditionNode, fieldName));
                } else {
                    // Process `=` condition
                    condition.set(condition.get().and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));
                }
            });
        }

        // Process JOIN conditions
        SelectJoinStep<Record> query = dslContext.select().from(DSL.table(tableName));
        if (joinArray != null) {
            for (JsonNode jsonNode : joinArray) {
                String joinType = jsonNode.get("type").asText(); // INNER, LEFT, RIGHT, FULL, CROSS, NATURAL, etc.
                String joinTable = jsonNode.get("table").asText(); // Join Table name
                String joinCondition = jsonNode.get("on").asText(); // Join condition
                switch (joinType.toUpperCase()) {
                    case "INNER" -> {
                        query.innerJoin(DSL.table(joinTable)).on(DSL.condition(joinCondition));
                    }
                    case "LEFT" -> {
                        query.leftJoin(DSL.table(joinTable)).on(DSL.condition(joinCondition));
                    }
                    case "RIGHT" -> {
                        query.rightJoin(DSL.table(joinTable)).on(DSL.condition(joinCondition));
                    }
                    case "FULL" -> {
                        query.fullJoin(DSL.table(joinTable)).on(DSL.condition(joinCondition));
                    }
                    case "CROSS" -> {
                        query.crossJoin(DSL.table(joinTable));
                    }
                    case "NATURAL" -> {
                        query.naturalJoin(DSL.table(joinTable));
                    }
                    default -> throw new IllegalArgumentException("Unsupported join type: " + joinType);
                }

            }
        }

        // Process GROUP BY conditions
        List<Field<?>> groupByFields = new ArrayList<>();
        if (groupByArray != null) {
            groupByArray.forEach(field -> {
                groupByFields.add(DSL.field(field.asText()));
            });
        }

        // Process HAVING conditions
        Condition havingCondition = DSL.noCondition();
        if (havingNode != null) {
            havingNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isArray()) {
                    // Process `IN` condition
                    List<String> values = new ArrayList<>();
                    entry.getValue().forEach(value -> {
                        values.add(value.asText());
                        havingCondition.and(DSL.field(entry.getKey()).in(values));
                    });
                } else {
                    // Process `=` condition
                    havingCondition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText()));
                }

            });
        }

        // Build the final query
        query.where(condition.get());

        // Add GROUP BY
        if (!groupByFields.isEmpty()) {
            query.groupBy(groupByFields);
        }

        // Add HAVING
        if (havingCondition != DSL.noCondition()) {
            query.having(havingCondition);
        }

        // Process ORDER BY conditions
        if (orderByArray != null) {
            List<SortField<?>> orderByFields = new ArrayList<>();
            for (JsonNode orderByNode : orderByArray) {
                String fieldName = orderByNode.get("field").asText();
                String dirction = orderByNode.get("direction").asText().toUpperCase(); // ASC or DESC
                Field<?> field = DSL.field(fieldName);
                SortField<?> sortField = dirction.equals("DESC") ? field.desc() : field.asc();
                orderByFields.add(sortField);
            }
            query.orderBy(orderByFields);
        }
        // add pagination
        int page = 0;
        int pageSize = 0;
        int offset = 0;
        long totalItem = 0L;
        if (paginationNode != null) {
            pageSize = paginationNode.get("pageSize").asInt();
            page = paginationNode.get("page").asInt();
            offset = (page - 1) * pageSize;

            query.limit(pageSize).offset(offset);
        }
        totalItem = DataAnalysisUtil.count(dslContext, tableName);
        PaginationInfo paginationInfo = new PaginationInfo(page, pageSize, totalItem,
                Math.toIntExact((totalItem + pageSize - 1) / pageSize));
        Result<Record> result = query.fetch();
        return new DataRecord("select join", "tables",
                Optional.of(convertToMapList(result)), Optional.of(paginationInfo));
    }

    @Override
    public DataRecord addForeignKey(DSLContext dslContext, JsonNode rootNode) {
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(addForeignKeyLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(addForeignKeyLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    @Override
    public DataRecord createIndex(DSLContext dslContext, JsonNode rootNode) {
        // 判断是否需要启用事务
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(createIndexLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(createIndexLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    @Override
    public DataRecord dropIndex(DSLContext dslContext, JsonNode rootNode) {
        // 判断是否需要启用事务
        boolean useTransaction = rootNode.has("use_transaction") &&
                rootNode.get("use_transaction").asBoolean();
        AtomicReference<DataRecord> result_ = new AtomicReference<>();
        if (useTransaction) {
            dslContext.transaction(configuration -> {
                result_.set(dropIndexLogic(configuration.dsl(), rootNode));
            });
        } else {
            result_.set(dropIndexLogic(dslContext, rootNode));
        }
        return result_.get();
    }

    private DataRecord createIndexLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String indexName = rootNode.get("index_name").asText();
//        String indexType = rootNode.has("index_type") ?
//                rootNode.get("index_type").asText() : "BTREE";
        JsonNode columnsNode = rootNode.get("columns");
        if (columnsNode == null || !columnsNode.isArray() || columnsNode.size() == 0) {
            log.warn("No columns defined for index: " + indexName);
            throw new IllegalArgumentException("No columns defined for index: " + indexName);
        }
        List<String> columns = new ArrayList<>();
        for (JsonNode columnNode : columnsNode) {
            columns.add(columnNode.asText());
        }

        // 判断是否是唯一索引
        boolean isUnique = rootNode.has("unique") && rootNode.get("unique").asBoolean();

        if (isUnique) {
            dslContext.createUniqueIndex(indexName)
                    .on(tableName, columns.toArray(new String[0])).execute();
            log.info("Create unique index: " + indexName);
            return new DataRecord("create unique index", indexName,
                    Optional.empty(), Optional.empty());
        } else {
            // 创建索引
            dslContext.createIndex(indexName)
                    .on(tableName, columns.toArray(new String[0])).execute();
            log.info("Create index: " + indexName);
            return new DataRecord("create index", indexName,
                    Optional.empty(), Optional.empty());
        }

        // 设置索引类型 ：目前jooq还不支持设置类型，后面主要功能后，再来实现
//        if ("BTREE".equalsIgnoreCase(indexType)) {
//            // todo
//        } else if ("HASH".equalsIgnoreCase(indexType)) {
//            // todo
//        } else if ("GIN".equalsIgnoreCase(indexType)) {
//            // todo
//        } else if ("GIST".equalsIgnoreCase(indexType)) {
//            // todo
//        } else {
//            throw new IllegalArgumentException("Unsupported index type: " + indexType);
//        }
    }

    private DataRecord dropIndexLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String indexName = rootNode.get("index_name").asText();

        dslContext.dropIndexIfExists(indexName).on(tableName).execute();
        log.info("Drop index: " + indexName);
        return new DataRecord("drop index", indexName,
                Optional.empty(), Optional.empty());
    }

    private DataRecord addForeignKeyLogic(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        String relationType = rootNode.get("relation_type").asText();
        JsonNode foreignKeyNode = rootNode.get("foreign_key");

        if (foreignKeyNode == null || !foreignKeyNode.isArray()) {
            log.warn("No foreign keys defined for table: " + tableName);
            return new DataRecord("No foreign keys defined for table: ", tableName, Optional.empty(), Optional.empty());
        }
        if ("N-N".equals(relationType)) {
            return createIntermediateTable(dslContext, tableName, foreignKeyNode);
        }

        // process 1-N or N-1
        for (JsonNode foreignKey : foreignKeyNode) {
            String foreignKeyName = foreignKey.get("name").asText();
            String column = foreignKeyNode.get("column").asText();
            String referencedTable = foreignKey.get("referenced_table").asText();
            String referencedColumn = foreignKey.get("referenced_column").asText();

            // create foreign key constraint
            ConstraintForeignKeyOnStep constraint = DSL.constraint(foreignKeyName)
                    .foreignKey(column).references(referencedTable, referencedColumn);

            // 处理级联操作
            if (foreignKey.has("on_delete")) {

                constraint = getReferentialAction(constraint,
                        foreignKey.asText(), foreignKey.get("on_delete").asText());
            }

            if (foreignKey.has("on_update")) {
                constraint = getReferentialAction(constraint,
                        foreignKey.asText(), foreignKey.get("on_update").asText());
            }
            dslContext.alterTable(tableName)
                    .add(constraint).execute();
            log.info("Added foreign key constraint: " + foreignKeyName + " to table: " + tableName);
        }
        return new DataRecord("add foreign key", tableName, Optional.empty(), Optional.empty());
    }

    private DataRecord createIntermediateTable(DSLContext dslContext, String tableName, JsonNode foreignKeysNode) {
        String intermediateTableName = tableName + "_" + foreignKeysNode.get("name").asText();
        // 创建中间表
        var createTableStep = dslContext.createTableIfNotExists(intermediateTableName);
        List<String> primaryKeys = new ArrayList<>();
        for (JsonNode foreignKeyNode : foreignKeysNode) {
            String column = foreignKeyNode.get("column").asText();
            String referencedTable = foreignKeyNode.get("referenced_table").asText();
            String referencedColumn = foreignKeyNode.get("referenced_column").asText();
            String columnType = foreignKeyNode.get("column_type").asText();
            // 添加列
            createTableStep.column(column, getSqlDataType(columnType).nullable(false));

            // 添加外键约束
            String foreignKeyName = foreignKeyNode.get("name").asText();
            createTableStep.constraint(DSL.constraint(foreignKeyName)
                    .foreignKey(column).references(referencedTable, referencedColumn));
            primaryKeys.add(column); // 将列名添加到主键列表中

            // 设置复合主键
            createTableStep.constraint(DSL.constraint("pk_" + intermediateTableName)
                            .primaryKey(primaryKeys.toArray(new String[0])))
                    .execute();
            log.info("Created intermediate table: " + intermediateTableName);
        }
        return new DataRecord("create intermediate table", intermediateTableName, Optional.empty(), Optional.empty());
    }

    private Condition operatorCondition(String operator, Condition condition,
                                        JsonNode valueNode, String fieldName) {
        switch (operator) {
            case "eq" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).eq(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).eq(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).eq(valueNode.asText());
                }
                return condition;
            }
            case "neq" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).ne(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).ne(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).ne(valueNode.asText());
                }
                return condition;
            }
            case "gt" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).gt(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).gt(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).gt(valueNode.asText());
                }
                return condition;
            }
            case "lt" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).lt(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).lt(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).lt(valueNode.asText());
                }
                return condition;
            }
            case "gte" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).ge(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).ge(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).ge(valueNode.asText());
                }
                return condition;
            }
            case "lte" -> {
                if (isNumber(valueNode.asText())) {
                    if (isInteger(valueNode.asText())) {
                        condition = DSL.condition(DSL.field(fieldName).le(valueNode.asInt()));
                    } else if (isFloat(valueNode.asText())) {
                        condition = DSL.field(fieldName).le(valueNode.asDouble());
                    }
                } else {
                    condition = DSL.field(fieldName).le(valueNode.asText());
                }
                return condition;
            }
            case "like" -> {
                condition = DSL.field(fieldName).like(valueNode.asText());
                return condition;
            }
            case "between" -> {
                if (valueNode.isArray() && valueNode.size() == 2) {
                    if (isNumber(valueNode.get(0).asText()) && isNumber(valueNode.get(1).asText())) {
                        if (isInteger(valueNode.get(0).asText()) && isInteger(valueNode.get(1).asText())) {
                            int lowerBound = valueNode.get(0).asInt();
                            int upperBound = valueNode.get(1).asInt();
                            condition = DSL.field(fieldName).between(lowerBound, upperBound);
                        }
                    } else if (isFloat(valueNode.get(0).asText()) && isFloat(valueNode.get(1).asText())) {
                        double lowerBound = valueNode.get(0).asDouble();
                        double upperBound = valueNode.get(1).asDouble();
                        condition = DSL.field(fieldName).between(lowerBound, upperBound);
                    } else {
                        String lowerBound = valueNode.get(0).asText();
                        String upperBound = valueNode.get(1).asText();
                        condition = DSL.field(fieldName).between(lowerBound, upperBound);
                    }

                    return condition;
                } else {
                    throw new IllegalArgumentException("Invalid value for 'between' operator: " + valueNode);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }

    private static String[] getAllPrimaryKeys(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(key).append(",");
        }
        return sb.toString().split(",");
    }

    private static boolean isNumber(String str) {
        String numberPattern = "^[-+]?\\d*\\.?\\d+$";
        return str.matches(numberPattern);
    }

    private static boolean isInteger(String str) {
        String integerPattern = "^[-+]?\\d+$";
        return str.matches(integerPattern);
    }

    private static boolean isFloat(String str) {
        String floatPattern = "^[-+]?\\d*\\.?\\d+([eE][-+]?\\d+)?$";
        return str.matches(floatPattern);
    }

    private static void modifyPrimaryKeyToAutoIncrement(DSLContext dslContext, String tableName, String primaryKeyColumn) {
        // 1. 删除主键约束
        dslContext.alterTable(tableName)
                .dropConstraintIfExists("pk_" + tableName).execute();
        // 2. 修改主键为自增主键
        String alterColumnSql = String.format("ALTER TABLE %s MODIFY COLUMN %s INT AUTO_INCREMENT", tableName, primaryKeyColumn);
        dslContext.execute(alterColumnSql);

        // 3. 重新添加主键约束
        dslContext.alterTable(tableName)
                .add(DSL.constraint("pk_" + tableName).primaryKey(primaryKeyColumn)).execute();
    }

    /**
     * @param actionName :[on_delete, on_update]
     * @param action
     * @return
     */
    private ConstraintForeignKeyOnStep getReferentialAction(ConstraintForeignKeyOnStep constraint,
                                                            String actionName,
                                                            String action) {
        if (actionName.equals("on_delete")) {
            return switch (action.toUpperCase()) {
                case "CASCADE" -> constraint.onDeleteCascade();
                case "SET NULL" -> constraint.onDeleteSetNull();
                case "RESTRICT" -> constraint.onDeleteRestrict();
                case "NO ACTION" -> constraint.onDeleteNoAction();
                default -> throw new IllegalArgumentException("Unsupported referential action: " + action);
            };
        } else if (actionName.equals("on_update")) {
            return switch (action.toUpperCase()) {
                case "CASCADE" -> constraint.onUpdateCascade();
                case "SET NULL" -> constraint.onUpdateSetNull();
                case "RESTRICT" -> constraint.onUpdateRestrict();
                case "NO ACTION" -> constraint.onUpdateNoAction();
                default -> throw new IllegalArgumentException("Unsupported referential action: " + action);
            };
        }
        return constraint;
    }

    /**
     * 创建删除表的JSON定义
     *
     * @param tableName 要删除的表名
     * @return 删除表的JSON定义
     */
    public JsonNode createDropTableJson(String tableName) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // 创建一个包含表名的简单JSON对象
            return objectMapper.createObjectNode()
                    .put("operation", "DROP")
                    .put("table", tableName);
        } catch (Exception e) {
            throw new RuntimeException("创建删除表JSON定义失败: " + e.getMessage(), e);
        }
    }
}
