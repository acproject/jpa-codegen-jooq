package com.owiseman.jpa.util;

import com.owiseman.jpa.model.ColumnMeta;
import com.owiseman.jpa.model.DataSourceEnum;

public interface MapToType {
    String mapToType(ColumnMeta col, DataSourceEnum dataSourceEnum);
}
