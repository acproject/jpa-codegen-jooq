package com.owiseman.jpa.model;

import java.util.List;
import java.util.Optional;

public record DataRecord(
        String pattern,
        String name,
        Optional<List<?>> load
) {
}
