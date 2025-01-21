package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.jooq.DSLContext;

/**
 * @author acproject@qq.com
 * @date 2025-01-18 20:20
 */
public interface TabaleAndDataOperation {
    void createTable(DSLContext dslContext, JsonNode rootNode );
    void dropTable(DSLContext dslContext, JsonNode rootNode);
    void alterTable(DSLContext dslContext, JsonNode rootNode);
    void insertData(DSLContext dslContext, JsonNode rootNode);
    void insertBatchData(DSLContext dslContext, JsonNode rootNode);
    void updateData(DSLContext dslContext, JsonNode rootNode);
    void updateBatchData(DSLContext dslContext, JsonNode rootNode);
    void deleteData(DSLContext dslContext, JsonNode rootNode);
    void SelectData(DSLContext dslContext, JsonNode rootNode);
    void SelectJoinData(DSLContext dslContext, JsonNode rootNode);
}
