package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import lombok.extern.log4j.Log4j;

import org.jooq.Condition;
import org.jooq.CreateTableColumnStep;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectHavingStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.groupBy;


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
        JsonNode rootNode = objectMapper.readTree(json);
        String operation = rootNode.get("operation").asText();
        switch (operation) {
            case "create_table" -> getInstance().createTable(dslContext, rootNode);
            case "drop_table" -> getInstance().dropTable(dslContext, rootNode);
            case "alter_table" -> getInstance().alterTable(dslContext, rootNode);
            case "insert" -> getInstance().insertData(dslContext, rootNode);
            case "insert_batch" -> getInstance().insertBatchData(dslContext, rootNode);
            case "update_data" -> getInstance().updateData(dslContext, rootNode);
            case "update_batch" -> getInstance().updateBatchData(dslContext, rootNode);
            case "delete" -> getInstance().deleteData(dslContext, rootNode);
            case "select" -> getInstance().SelectData(dslContext, rootNode);
            case "select_batch" -> getInstance().SelectJoinData(dslContext, rootNode);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        getInstance().sendToMQ(rabbitTemplate, json);
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

    public void createTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode columns = rootNode.get("columns");

        CreateTableColumnStep createTableStep = dslContext.createTable(tableName);

        for (JsonNode column : columns) {
            String columnName = column.get("name").asText();
            String columnType = column.get("type").asText();
            createTableStep.column(columnName, getSqlDataType(columnType));
        }
        createTableStep.execute();
        log.info("Create table: " + tableName);
    }

    private static DataType<?> getSqlDataType(String typeName) {
        return switch (typeName) {
            case "int", "java.lang.Integer" -> SQLDataType.INTEGER;
            case "long", "java.lang.Long" -> SQLDataType.BIGINT;
            case "String", "java.lang.String" -> SQLDataType.VARCHAR;
            case "LocalDate", "java.time.LocalDate" -> SQLDataType.LOCALDATE;
            case "LocalDateTime", "java.time.LocalDateTime" -> SQLDataType.LOCALDATETIME;
            case "LocalTime", "java.time.LocalTime" -> SQLDataType.LOCALTIME;
            case "OffsetDateTime", "java.time.OffsetDateTime", "java.util.Date" -> SQLDataType.DATE;
            case "JSONB", "org.jooq.JSONB" -> SQLDataType.JSONB;
            case "Boolean", "java.lang.Boolean" -> SQLDataType.BOOLEAN;
            case "JSON", "org.jooq.JSON" -> SQLDataType.JSON;
            default -> SQLDataType.OTHER;
        };
    }

    @Override
    public void dropTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        dslContext.dropTable(tableName).execute();
        log.info("Drop table: " + tableName);
    }

    @Override
    public void alterTable(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode columns = rootNode.get("columns");

        for (JsonNode column : columns) {
            String columnName = column.get("name").asText();
            String operation = column.has("operation") ? column.get("operation").asText() : "add";
            String columnType = column.get("type").asText();
            switch (operation.toLowerCase()) {
                case "add" -> {
                    if (columnType == null)
                        throw new IllegalArgumentException("Column type is required for add opertion");
                    dslContext.alterTable(tableName).addColumn(columnName, getSqlDataType(columnType)).execute();
                }
                case "modify" -> {
                    if (columnType == null)
                        throw new IllegalArgumentException("Column type is required for modify opertion");
                    dslContext.alterTable(tableName).alterColumn(columnName).set(getSqlDataType(columnType)).execute();
                }
                case "drop" -> dslContext.alterTable(tableName).dropColumn(columnName).execute();
                default -> throw new IllegalArgumentException("Column type is required for add opertion");
            }
        }
        log.info("Table altered: " + tableName);
    }

    @Override
    public void insertData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataNode = rootNode.get("data");

        Map<String, Object> data = new HashMap<>();
        dataNode.fields().forEachRemaining(entry -> data.put(entry.getKey(),
                entry.getValue().asText()));
        dslContext.insertInto(DSL.table(tableName))
                .set(data)
                .execute();
        log.info("Insert data into table: " + tableName);
    }

    @Override
    public void insertBatchData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataArray = rootNode.get("data");
        List<Map<String, Object>> dataList = new ArrayList<>();

        for (JsonNode dataNode : dataArray) {
            Map<String, Object> data = new HashMap<>();
            dataNode.fields().forEachRemaining(entry ->
                    data.put(entry.getKey(), entry.getValue().asText()));
            dataList.add(data);
        }
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

    @Override
    public void updateData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataNode = rootNode.get("data");
        JsonNode whereNode = rootNode.get("where");

        Map<String, Object> data = new HashMap<>();
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
    }

    @Override
    public void updateBatchData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode dataArray = rootNode.get("data");
        for (JsonNode dataNode : dataArray) {
            JsonNode valuesNode = dataNode.get("values");
            JsonNode whereNode = dataNode.get("where");

            Map<String, Object> data = new HashMap<>();
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
    }

    @Override
    public void deleteData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");

        Condition condition = DSL.noCondition();
        whereNode.fields().forEachRemaining(entry ->
                condition.and(DSL.field(entry.getKey()).eq(entry.getValue().asText())));
        dslContext.deleteFrom(DSL.table(tableName))
                .where(condition)
                .execute();
        log.info("Delete data from table: " + tableName);
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
    public void SelectData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereNode = rootNode.get("where");
        JsonNode groupByNode = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");

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

        Result<Record> result = selectStep.fetch();
        result.forEach(record -> {
            System.out.println(record);
        });

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
     * "customers.country": "USA"
     * },
     * "groupBy": ["customers.id", "orders.status"],
     * "having": {
     * "COUNT(orders.id)": "5"
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
    public void SelectJoinData(DSLContext dslContext, JsonNode rootNode) {
        String tableName = rootNode.get("table").asText();
        JsonNode whereArray = rootNode.get("where");
        JsonNode joinArray = rootNode.get("join");
        JsonNode groupByArray = rootNode.get("groupBy");
        JsonNode havingNode = rootNode.get("having");

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
                } else if(conditionNode.isObject()) {
                    operatorCondition(conditionNode.get("operator").asText(),
                            condition, conditionNode, fieldName);
                }else {
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

        Result<Record> result = query.fetch();
        result.forEach(record -> {
            System.out.println(record);
        });

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
}
