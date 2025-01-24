package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.owiseman.jpa.model.DataRecord;
import lombok.extern.log4j.Log4j;

import org.jooq.Condition;
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
                return getInstance().SelectData(dslContext, rootNode);
            }
            case "select_batch" -> {
                return getInstance().SelectJoinData(dslContext, rootNode);
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

    public DataRecord createTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode columns = rootNode.get("columns");
        JsonNode primaryKeysNode = rootNode.get("primary_keys");
        JsonNode uniqueKeysNode = rootNode.get("unique_keys");

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

        createTableStep.execute();
        DataRecord dataRecord = new DataRecord("create table", tableName, null);
        log.info("Create table: " + tableName);
        return dataRecord;
    }

    private static DataType getSqlDataType(String typeName) {
        return switch (typeName) {
            case "int", "java.lang.Integer", "INTEGER" -> SQLDataType.INTEGER;
            case "long", "java.lang.Long" -> SQLDataType.BIGINT;
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
        dslContext.dropDatabaseIfExists(tableName).execute();
        log.info("Drop table: " + tableName);
        return new DataRecord("drop table", tableName, null);
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
        return new DataRecord("alter table", tableName, null);
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
        return new DataRecord("insert", tableName, null);
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
        return new DataRecord("insert batch", tableName, null);
    }

    @Override
    public DataRecord updateData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataNode = rootNode.get("data");
        JsonNode whereNode = rootNode.get("where");

        Map<String, Object> data = new LinkedHashMap<>();
        dataNode.fields().forEachRemaining(entry ->
                data.put(entry.getKey(), entry.getValue().asText()));
        Condition condition = DSL.noCondition();
        whereNode.fields().forEachRemaining(entry ->
                condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));
        dslContext.update(DSL.table(tableName))
                .set(data)
                .where(condition)
                .execute();
        log.info("Update data into table: " + tableName);
        return new DataRecord("update", tableName, null);
    }

    @Override
    public DataRecord updateBatchData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataArray = rootNode.get("data");
        for (JsonNode dataNode : dataArray) {
            JsonNode valuesNode = dataNode.get("values");
            JsonNode whereNode = dataNode.get("where");

            Map<String, Object> data = new LinkedHashMap<>();
            valuesNode.fields().forEachRemaining(entry ->
                    data.put(entry.getKey(), entry.getValue().asText()));
            Condition condition = DSL.noCondition();
            whereNode.fields().forEachRemaining(entry ->
                    condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));

            dslContext.update(DSL.table(tableName))
                    .set(data)
                    .where(condition)
                    .execute();
        }
        log.info("Update batch data into table: " + tableName);
        return new DataRecord("update batch", tableName, null);
    }

    @Override
    public DataRecord deleteData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");

        Condition condition = DSL.noCondition();
        whereNode.fields().forEachRemaining(entry ->
                condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));
        dslContext.deleteFrom(DSL.table(tableName))
                .where(condition)
                .execute();
        log.info("Delete data from table: " + tableName);
        return new DataRecord("delete", tableName, null);
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
    public DataRecord SelectData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");
        JsonNode groupByNode = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");
        JsonNode orderByNode = rootNode.get("orderBy");
        JsonNode paginationNode = rootNode.get("pagination");

        Condition condition = DSL.noCondition();
        // 构建WHERE条件子句
        if (whereNode != null) {
            whereNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode conditionNode = entry.getValue();
                if (conditionNode.isArray()) {
                    List<String> values = new ArrayList<>();
                    conditionNode.forEach(value -> values.add(value.asText()));
                    condition.and(DSL.field(fieldName).in(values));
                } else if (conditionNode.isObject()) {
                    String operator = conditionNode.get("operator").asText();
                    JsonNode valueNode = conditionNode.get("value");
                    operatorCondition(operator, condition, valueNode, fieldName);
                } else {
                    // default use eq condition
                    condition.and(DSL.field(fieldName).eq(conditionNode.asText()));
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
                .where(condition);

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

        // process pagination
        if (paginationNode != null) {
            int page = paginationNode.get("page").asInt();
            int pageSize = paginationNode.get("pageSize").asInt();
            int offset = (page - 1) * pageSize;

            selectStep.limit(pageSize).offset(offset);
        }

        Result<Record> result = selectStep.fetch();
        return new DataRecord("select", tableName,
                Optional.of(result.stream().toList()));
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
    @Override
    public DataRecord SelectJoinData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereArray = rootNode.get("where");
        JsonNode joinArray = rootNode.get("join");
        JsonNode groupByArray = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");
        JsonNode orderByArray = rootNode.get("orderByArr");
        JsonNode paginationNode = rootNode.get("pagination");

        Condition condition = DSL.noCondition();

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
                        condition.and(DSL.field(entry.getKey()).in(values));
                    });
                } else if (conditionNode.isObject()) {
                    operatorCondition(conditionNode.get("operator").asText(),
                            condition, conditionNode, fieldName);
                } else {
                    // Process `=` condition
                    condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText()));
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
        query.where(condition);

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
        if (paginationNode != null) {
            int pageSize = paginationNode.get("pageSize").asInt();
            int page = paginationNode.get("page").asInt();
            int offset = (page - 1) * pageSize;

            query.limit(pageSize).offset(offset);
        }

        Result<Record> result = query.fetch();
        return new DataRecord("select join", "tables",
                Optional.of(result.stream().toList()));
    }

    private Condition operatorCondition(String operator, Condition condition,
                                        JsonNode valueNode, String fieldName) {
        switch (operator) {
            case "eq" -> {
                condition.and(DSL.field(fieldName).eq(valueNode.asText()));
                return condition;
            }
            case "neq" -> {
                condition.and(DSL.field(fieldName).ne(valueNode.asText()));
                return condition;
            }
            case "gt" -> {
                condition.and(DSL.field(fieldName).gt(valueNode.asText()));
                return condition;
            }
            case "lt" -> {
                condition.and(DSL.field(fieldName).lt(valueNode.asText()));
                return condition;
            }
            case "gte" -> {
                condition.and(DSL.field(fieldName).ge(valueNode.asText()));
                return condition;
            }
            case "lte" -> {
                condition.and(DSL.field(fieldName).le(valueNode.asText()));
                return condition;
            }
            case "between" -> {
                if (valueNode.isArray() && valueNode.size() == 2) {
                    String lowerBound = valueNode.get(0).asText();
                    String upperBound = valueNode.get(1).asText();
                    condition.and(DSL.field(fieldName).between(lowerBound, upperBound));
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
}
