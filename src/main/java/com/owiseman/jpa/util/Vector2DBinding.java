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
 * 用于处理 PostgreSQL vector[](n) 类型的自定义绑定
 */
@Deprecated
public class Vector2DBinding implements Binding<Object, Float[][]> {

    @Override
    public Converter<Object, Float[][]> converter() {
        return new Converter<>() {
            @Override
            public Float[][] from(Object databaseObject) {
                if (databaseObject == null) {
                    return null;
                }

                try {
                    if (databaseObject instanceof Array) {
                        Array array = (Array) databaseObject;
                        Object[] objArray = (Object[]) array.getArray();
                        Float[][] result = new Float[objArray.length][];

                        for (int i = 0; i < objArray.length; i++) {
                            if (objArray[i] instanceof Array) {
                                Array innerArray = (Array) objArray[i];
                                Object[] innerObjArray = (Object[]) innerArray.getArray();
                                Float[] innerResult = new Float[innerObjArray.length];
                                
                                for (int j = 0; j < innerObjArray.length; j++) {
                                    if (innerObjArray[j] instanceof Number) {
                                        innerResult[j] = ((Number) innerObjArray[j]).floatValue();
                                    }
                                }
                                
                                result[i] = innerResult;
                            }
                        }

                        return result;
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error converting database vector[] to float[][] array", e);
                }

                return null;
            }

            @Override
            public Object to(Float[][] userObject) {
                if (userObject == null) {
                    return null;
                }

                // 将 Float[][] 转换为 PostgreSQL vector[] 格式的字符串表示
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < userObject.length; i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    
                    sb.append("[");
                    for (int j = 0; j < userObject[i].length; j++) {
                        if (j > 0) {
                            sb.append(",");
                        }
                        sb.append(userObject[i][j]);
                    }
                    sb.append("]");
                }
                sb.append("]");

                return sb.toString();
            }

            @Override
            public Class<Object> fromType() {
                return Object.class;
            }

            @Override
            public Class<Float[][]> toType() {
                return Float[][].class;
            }
        };
    }

    @Override
    public void sql(BindingSQLContext<Float[][]> ctx) throws SQLException {
        ctx.render().visit(DSL.sql("?::vector[]"));
    }

    @Override
    public void register(BindingRegisterContext<Float[][]> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.ARRAY);
    }

    @Override
    public void set(BindingSetStatementContext<Float[][]> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.statement().setNull(ctx.index(), Types.ARRAY);
        } else {
            ctx.statement().setString(ctx.index(), String.valueOf(converter().to(ctx.value())));
        }
    }

    @Override
    public void get(BindingGetResultSetContext<Float[][]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getObject(ctx.index()));
    }

    @Override
    public void get(BindingGetStatementContext<Float[][]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getObject(ctx.index()));
    }

    @Override
    public void set(BindingSetSQLOutputContext<Float[][]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(BindingGetSQLInputContext<Float[][]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}