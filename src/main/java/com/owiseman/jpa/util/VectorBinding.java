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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用于处理 PostgreSQL pgvector 类型的自定义绑定
 */
public class VectorBinding implements Binding<Object, Float[]> {

    @Override
    public Converter<Object, Float[]> converter() {
        return new Converter<>() {
            @Override
            public Float[] from(Object databaseObject) {
                if (databaseObject == null) {
                    return null;
                }

                try {
                    // 处理Array类型
                    if (databaseObject instanceof Array) {
                        Array array = (Array) databaseObject;
                        Object[] objArray = (Object[]) array.getArray();
                        Float[] result = new Float[objArray.length];

                        for (int i = 0; i < objArray.length; i++) {
                            if (objArray[i] instanceof Number) {
                                result[i] = ((Number) objArray[i]).floatValue();
                            }
                        }

                        return result;
                    } 
                    // 处理字符串类型，例如 "[0.1,0.2,0.3]"
                    else if (databaseObject instanceof String || databaseObject instanceof java.sql.Clob) {
                        String vectorStr = databaseObject.toString();
                        return parseVectorString(vectorStr);
                    }
                    // 处理PGobject或其他类型
                    else {
                        String vectorStr = databaseObject.toString();
                        return parseVectorString(vectorStr);
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error converting database vector to float array", e);
                } catch (Exception e) {
                    throw new DataAccessException("Error parsing vector data: " + databaseObject, e);
                }
            }

            private Float[] parseVectorString(String vectorStr) {
                // 移除前后的方括号
                if (vectorStr.startsWith("[") && vectorStr.endsWith("]")) {
                    vectorStr = vectorStr.substring(1, vectorStr.length() - 1);
                }
                
                // 使用正则表达式提取所有浮点数
                List<Float> values = new ArrayList<>();
                Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?([Ee][-+]?\\d+)?");
                Matcher matcher = pattern.matcher(vectorStr);
                
                while (matcher.find()) {
                    values.add(Float.parseFloat(matcher.group()));
                }
                
                return values.toArray(new Float[0]);
            }

            @Override
            public Object to(Float[] userObject) {
                if (userObject == null) {
                    return null;
                }

                // 将 Float[] 转换为 PostgreSQL vector 格式的字符串表示
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
            public Class<Float[]> toType() {
                return Float[].class;
            }
        };
    }

    @Override
    public void sql(BindingSQLContext<Float[]> ctx) throws SQLException {
        // 确保正确转换为vector类型
        ctx.render().visit(DSL.sql("?::vector"));
    }

    @Override
    public void register(BindingRegisterContext<Float[]> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.OTHER);
    }

    @Override
    public void set(BindingSetStatementContext<Float[]> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.statement().setNull(ctx.index(), Types.OTHER);
        } else {
            // 将Float[]转换为字符串并设置到语句中
            ctx.statement().setString(ctx.index(), String.valueOf(converter().to(ctx.value())));
        }
    }

    @Override
    public void get(BindingGetResultSetContext<Float[]> ctx) throws SQLException {
        // 添加日志以便调试
        Object obj = ctx.resultSet().getObject(ctx.index());
        if (obj != null) {
            System.out.println("Vector data type: " + obj.getClass().getName() + ", value: " + obj);
        }
        ctx.convert(converter()).value(obj);
    }

    @Override
    public void get(BindingGetStatementContext<Float[]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getObject(ctx.index()));
    }

    @Override
    public void set(BindingSetSQLOutputContext<Float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(BindingGetSQLInputContext<Float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}