package com.owiseman.jpa.util;

import org.jooq.Converter;
import org.postgresql.util.PGobject;

import java.util.Arrays;

public class PgVectorArrayConverter implements Converter<Object, Float[]> {
    @Override
    public Class<Object> fromType() {
        return Object.class;
    }

    @Override
    public Class<Float[]> toType() {
        return Float[].class;
    }

    @Override
    public Float[] from(Object databaseObject) {
        if (databaseObject == null) return null;

        try {
            String value;
            if (databaseObject instanceof PGobject) {
                value = ((PGobject) databaseObject).getValue();
            } else {
                value = databaseObject.toString();
            }

            // value = "[0.1, 0.2, 0.3]"
            value = value.replaceAll("[\\[\\]]", "");
            String[] parts = value.split(",");
            Float[] result = new Float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert vector", e);
        }
    }

    @Override
    public Object to(Float[] userObject) {
        if (userObject == null) return null;
        var values = Arrays.toString(userObject);
        return "[" + values + "]";
    }
}
