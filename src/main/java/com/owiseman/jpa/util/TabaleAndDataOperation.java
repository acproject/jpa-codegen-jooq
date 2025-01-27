package com.owiseman.jpa.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.owiseman.jpa.model.DataRecord;
import org.jooq.DSLContext;


/**
 * @author acproject@qq.com
 * @date 2025-01-18 20:20
 */
public interface TabaleAndDataOperation {
    // 通过json创建表，逻辑比较简单，所以不加事务
    DataRecord createTable(DSLContext dslContext, JsonNode rootNode );
    // 通过json批量创建表，因为批量创建表需要事务，所以加事务
    DataRecord createBatchTable(DSLContext dslContext, JsonNode rootNode);
    // 通过json删除表，逻辑比较简单，所以不加事务
    DataRecord dropTable(DSLContext dslContext, JsonNode rootNode);
    // 通过json修改表
    DataRecord alterTable(DSLContext dslContext, JsonNode rootNode);
    // 通过json插入数据
    DataRecord insertData(DSLContext dslContext, JsonNode rootNode);
    // 通过json批量插入数据
    DataRecord insertBatchData(DSLContext dslContext, JsonNode rootNode);
    // 通过json更新数据
    DataRecord updateData(DSLContext dslContext, JsonNode rootNode);
    // 通过json批量更新数据
    DataRecord updateBatchData(DSLContext dslContext, JsonNode rootNode);
    // 通过json删除数据，物理删除
    DataRecord deleteData(DSLContext dslContext, JsonNode rootNode);
    // 通过json查询数据（复杂单表查询）
    DataRecord selectData(DSLContext dslContext, JsonNode rootNode);
    // 通过json查询数据（复杂多表查询）
    DataRecord selectJoinData(DSLContext dslContext, JsonNode rootNode);
    // 通过json添加外键，实现多表关联
    DataRecord addForeignKey(DSLContext dslContext, JsonNode rootNode);
    // 通过json创建索引
    DataRecord createIndex(DSLContext dslContext, JsonNode rootNode);
    // 通过json删除索引
    DataRecord dropIndex(DSLContext dslContext, JsonNode rootNode);

}
