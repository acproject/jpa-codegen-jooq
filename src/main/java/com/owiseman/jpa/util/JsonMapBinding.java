package com.owiseman.jpa.util;

import org.jooq.*;
import org.jooq.impl.DSL;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Map;

public class JsonMapBinding implements Binding<Object, Map<String, Object>> {
    // 添加默认构造函数以便在代码生成时实例化
    public JsonMapBinding() {}

    private final JsonMapConverter converter = new JsonMapConverter();

    @Override
    public Converter<Object, Map<String, Object>> converter() {
        return converter;
    }

    @Override
    public void sql(BindingSQLContext<Map<String, Object>> ctx) throws SQLException {
        // 使用PostgreSQL的CAST操作符将值转换为jsonb
        ctx.render().visit(DSL.val(ctx.convert(converter()).value())).sql("::jsonb");
    }

    @Override
    public void register(BindingRegisterContext<Map<String, Object>> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override
    public void set(BindingSetStatementContext<Map<String, Object>> ctx) throws SQLException {
        Object value = ctx.convert(converter()).value();
        if (value == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
        } else {
            ctx.statement().setObject(ctx.index(), value, Types.OTHER);
        }
    }

    @Override
    public void get(BindingGetResultSetContext<Map<String, Object>> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getObject(ctx.index()));
    }

    @Override
    public void get(BindingGetStatementContext<Map<String, Object>> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getObject(ctx.index()));
    }

    @Override
    public void set(BindingSetSQLOutputContext<Map<String, Object>> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(BindingGetSQLInputContext<Map<String, Object>> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}