package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import org.jooq.DSLContext;

/**
 * @author acproject@qq.com
 * @date 2025-01-18 20:20
 */
public interface TabaleAndDataOperation {
    DataRecord createTable(DSLContext dslContext, JsonNode rootNode );
    DataRecord dropTable(DSLContext dslContext, JsonNode rootNode);
    DataRecord alterTable(DSLContext dslContext, JsonNode rootNode);
    DataRecord insertData(DSLContext dslContext, JsonNode rootNode);
    DataRecord insertBatchData(DSLContext dslContext, JsonNode rootNode);
    DataRecord updateData(DSLContext dslContext, JsonNode rootNode);
    DataRecord updateBatchData(DSLContext dslContext, JsonNode rootNode);
    DataRecord deleteData(DSLContext dslContext, JsonNode rootNode);
    DataRecord SelectData(DSLContext dslContext, JsonNode rootNode);
    DataRecord SelectJoinData(DSLContext dslContext, JsonNode rootNode);
}
