package com.owiseman.jpa.util;

import org.jooq.Converter;
import org.postgresql.util.PGobject;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PgVector2DArrayConverter implements Converter<Object, Float[][]> {

    @Override
    public Class<Object> fromType() {
        return Object.class;
    }

    @Override
    public Class<Float[][]> toType() {
        return Float[][].class;
    }

    @Override
    public Float[][] from(Object databaseObject) {
        if (databaseObject == null) return null;

        try {
            String value;
            if (databaseObject instanceof PGobject) {
                value = ((PGobject) databaseObject).getValue();
            } else {
                value = databaseObject.toString();
            }

            // 示例: {{0.1, 0.2}, {0.3, 0.4}}
            value = value.replaceAll("^\\{\\{|}}$", ""); // 去掉最外层 {{ }}
            String[] vectorStrings = value.split("\\},\\s*\\{"); // 按单个 vector 拆分

            Float[][] result = new Float[vectorStrings.length][];
            for (int i = 0; i < vectorStrings.length; i++) {
                String cleaned = vectorStrings[i].replaceAll("[\\{\\}]", "");
                String[] parts = cleaned.split(",");
                Float[] vector = new Float[parts.length];
                for (int j = 0; j < parts.length; j++) {
                    vector[j] = Float.parseFloat(parts[j].trim());
                }
                result[i] = vector;
            }

            return result;

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert PostgreSQL vector[] to float[][]", e);
        }
    }

    @Override
    public Object to(Float[][] userObject) {
        if (userObject == null) return null;

        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < userObject.length; i++) {
            sb.append("[");
            for (int j = 0; j < userObject[i].length; j++) {
                sb.append(userObject[i][j]);
                if (j < userObject[i].length - 1) sb.append(",");
            }
            sb.append("]");
            if (i < userObject.length - 1) sb.append(",");
        }
        sb.append("}");
        sb.append("::vector[]");
        return sb.toString(); // 返回字符串形式的数组，如 '{{[0.1,0.2]},{[0.3,0.4]}}'
    }
}
