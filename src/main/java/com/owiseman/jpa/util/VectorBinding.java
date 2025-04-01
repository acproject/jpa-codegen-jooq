package com.owiseman.jpa.util;

import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

/**
 * 用于处理 PostgreSQL pgvector 类型的自定义绑定
 */
public class VectorBinding implements Binding<Object, float[]> {

    @Override
    public Converter<Object, float[]> converter() {
        return new Converter<>() {
            @Override
            public float[] from(Object databaseObject) {
                if (databaseObject == null) {
                    return null;
                }
                
                try {
                    if (databaseObject instanceof Array) {
                        Array array = (Array) databaseObject;
                        Object[] objArray = (Object[]) array.getArray();
                        float[] result = new float[objArray.length];
                        
                        for (int i = 0; i < objArray.length; i++) {
                            if (objArray[i] instanceof Number) {
                                result[i] = ((Number) objArray[i]).floatValue();
                            }
                        }
                        
                        return result;
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error converting database vector to float array", e);
                }
                
                return null;
            }

            @Override
            public Object to(float[] userObject) {
                if (userObject == null) {
                    return null;
                }
                
                // 将 float[] 转换为 PostgreSQL vector 格式的字符串表示
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < userObject.length; i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(userObject[i]);
                }
                sb.append("]");
                
                return sb.toString();
            }

            @Override
            public Class<Object> fromType() {
                return Object.class;
            }

            @Override
            public Class<float[]> toType() {
                return float[].class;
            }
        };
    }

    @Override
    public void sql(BindingSQLContext<float[]> ctx) throws SQLException {
        ctx.render().visit(DSL.sql("?::vector"));
    }

    @Override
    public void register(BindingRegisterContext<float[]> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override
    public void set(BindingSetStatementContext<float[]> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
        } else {
            ctx.statement().setString(ctx.index(), String.valueOf(converter().to(ctx.value())));
        }
    }

    @Override
    public void get(BindingGetResultSetContext<float[]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getObject(ctx.index()));
    }

    @Override
    public void get(BindingGetStatementContext<float[]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getObject(ctx.index()));
    }

    @Override
    public void set(BindingSetSQLOutputContext<float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(BindingGetSQLInputContext<float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}