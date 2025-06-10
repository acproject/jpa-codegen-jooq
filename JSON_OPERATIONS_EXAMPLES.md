# JSON/JSONB 数据操作示例

本文档展示了如何使用扩展后的 `TableAndDataUtil` 进行 JSON/JSONB 数据类型操作。

## 支持的操作类型

基于阿里云PostgreSQL文档，我们支持以下JSON/JSONB操作：

### 1. JSON路径查询 (`json_path_query`)

支持 `->`, `->>`, `#>`, `#>>` 操作符

```json
{
  "operation": "json_path_query",
  "table": "user_profiles",
  "json_column": "profile_data",
  "json_operation": "->",
  "path": "name"
}
```

```json
{
  "operation": "json_path_query", 
  "table": "orders",
  "json_column": "order_details",
  "json_operation": "#>",
  "path": ["customer", "address", "city"]
}
```

### 2. JSON包含查询 (`json_contains_query`)

支持 `@>`, `<@` 操作符

```json
{
  "operation": "json_contains_query",
  "table": "products",
  "json_column": "attributes",
  "contains_operation": "@>", 
  "json_value": "{\"color\": \"red\"}"
}
```

### 3. JSON键存在查询 (`json_keys_query`)

支持 `?`, `?|`, `?&` 操作符

```json
{
  "operation": "json_keys_query",
  "table": "user_preferences", 
  "json_column": "settings",
  "key_operation": "?",
  "keys": "theme"
}
```

```json
{
  "operation": "json_keys_query",
  "table": "user_preferences",
  "json_column": "settings", 
  "key_operation": "?|",
  "keys": ["theme", "language", "timezone"]
}
```

### 4. JSON路径更新 (`json_path_update`)

使用 `jsonb_set` 函数更新指定路径的值

```json
{
  "operation": "json_path_update",
  "table": "user_profiles",
  "json_column": "profile_data", 
  "path": "email",
  "new_value": "newemail@example.com",
  "where": {
    "user_id": "123"
  }
}
```

### 5. JSON键值修改 (`json_keys_modify`)

添加或删除JSON对象中的键值对

**添加键值对：**
```json
{
  "operation": "json_keys_modify",
  "table": "user_settings",
  "json_column": "preferences",
  "modify_operation": "add",
  "modifications": {
    "new_setting": "value",
    "another_key": "another_value"
  },
  "where": {
    "user_id": "123"
  }
}
```

**删除键：**
```json
{
  "operation": "json_keys_modify",
  "table": "user_settings", 
  "json_column": "preferences",
  "modify_operation": "remove",
  "modifications": ["old_setting", "deprecated_key"],
  "where": {
    "user_id": "123"
  }
}
```

### 6. JSON函数执行 (`json_function`)

执行各种JSON处理函数

```json
{
  "operation": "json_function",
  "table": "analytics_data",
  "json_column": "event_data", 
  "function": "json_array_length"
}
```

```json
{
  "operation": "json_function",
  "table": "user_data",
  "json_column": "metadata",
  "function": "json_object_keys"
}
```

### 7. 数据转换为JSON (`json_convert`)

将其他数据类型转换为JSON

```json
{
  "operation": "json_convert",
  "table": "users",
  "source_column": "name",
  "convert_function": "to_json"
}
```

```json
{
  "operation": "json_convert", 
  "table": "orders",
  "source_column": "items_array",
  "convert_function": "array_to_json"
}
```

### 8. JSON数据解析 (`json_parse`)

将JSON数据解析为记录或记录集

```json
{
  "operation": "json_parse",
  "table": "raw_data",
  "json_column": "json_content",
  "parse_function": "json_to_record"
}
```

### 9. JSON数组聚合 (`json_array_aggregate`)

对JSON数组执行聚合操作

```json
{
  "operation": "json_array_aggregate",
  "table": "event_logs",
  "json_column": "event_array", 
  "aggregate_function": "json_array_elements"
}
```

```json
{
  "operation": "json_array_aggregate",
  "table": "shopping_carts",
  "json_column": "items",
  "aggregate_function": "json_array_length"
}
```

### 10. JSON对象聚合 (`json_object_aggregate`)

对JSON对象执行聚合操作

```json
{
  "operation": "json_object_aggregate", 
  "table": "configuration",
  "json_column": "config_data",
  "aggregate_function": "json_object_keys"
}
```

```json
{
  "operation": "json_object_aggregate",
  "table": "dynamic_fields", 
  "json_column": "field_data",
  "aggregate_function": "json_typeof"
}
```

## 实际使用场景

### 电商场景

**查询红色商品：**
```json
{
  "operation": "json_contains_query",
  "table": "products", 
  "json_column": "attributes",
  "contains_operation": "@>",
  "json_value": "{\"color\": \"red\"}"
}
```

**更新用户购物车：**
```json
{
  "operation": "json_keys_modify",
  "table": "shopping_carts",
  "json_column": "cart_data", 
  "modify_operation": "add",
  "modifications": {
    "item_123": {
      "quantity": 2,
      "price": 29.99
    }
  },
  "where": {
    "user_id": "456"
  }
}
```

### 用户配置管理

**检查用户是否启用了特定功能：**
```json
{
  "operation": "json_keys_query",
  "table": "user_settings",
  "json_column": "feature_flags", 
  "key_operation": "?",
  "keys": "dark_mode"
}
```

**获取用户所有配置键：**
```json
{
  "operation": "json_object_aggregate",
  "table": "user_settings",
  "json_column": "preferences",
  "aggregate_function": "json_object_keys"
}
```

## 注意事项

1. **JSONB vs JSON**: 建议使用JSONB类型，因为它支持索引且性能更好
2. **索引创建**: 对于频繁查询的JSON字段，可以创建GIN索引提高性能
3. **事务支持**: 大部分操作都支持通过设置 `"use_transaction": true` 来启用事务
4. **路径语法**: JSON路径使用PostgreSQL标准语法，数组使用数字索引，对象使用键名

## 性能优化建议

1. 为JSONB列创建GIN索引：
```json
{
  "operation": "create_index",
  "table": "user_data", 
  "index_name": "idx_user_data_jsonb",
  "columns": ["profile_data"],
  "index_type": "GIN"
}
```

2. 使用包含查询（@>）而不是等值查询来利用索引
3. 尽量使用JSONB操作符而不是函数调用
4. 对于复杂查询，考虑将常用字段提取到单独的列中 