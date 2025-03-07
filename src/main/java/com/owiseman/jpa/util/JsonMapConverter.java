package com.owiseman.jpa.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Converter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonMapConverter implements Converter<Object, Map<String, Object>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public Map<String, Object> from(Object databaseObject) {
        if (databaseObject == null) {
            return null;
        }

        if (databaseObject instanceof List) {
            return objectMapper.convertValue(databaseObject, new TypeReference<HashMap<String, Object>>() {});
        }
        
        try {
            if (databaseObject instanceof String) {
                return objectMapper.readValue((String) databaseObject, new TypeReference<HashMap<String, Object>>() {});
            } else {
                // PostgreSQL的JSONB类型可能会以PGobject形式返回
                return objectMapper.readValue(databaseObject.toString(), new TypeReference<HashMap<String, Object>>() {});
            }
        } catch (IOException e) {
            throw new RuntimeException("Error converting database object to Map", e);
        }
    }

    @Override
    public Object to(Map<String, Object> userObject) {
        if (userObject == null) {
            return null;
        }
        
        try {
            if (userObject instanceof List<?>) {
                return objectMapper.writeValueAsString(userObject);
            }
            return objectMapper.writeValueAsString(userObject);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting Map to database object", e);
        }
    }

    @Override
    public Class<Object> fromType() {
        return Object.class;
    }

    @Override
    public Class<Map<String, Object>> toType() {
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> result = (Class<Map<String, Object>>) (Class<?>) Map.class;
        return result;
    }
}