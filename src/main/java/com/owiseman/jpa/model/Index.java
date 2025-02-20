package com.owiseman.jpa.model;

import java.util.List;

public record Index(
        String name,
        List<String> columns
) {
}
