package com.nbys.activity.service;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public class Rows {
    public static List<Map<String, Object>> list(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.query(sql, args, new ColumnMapRowMapper());
    }

    public static Map<String, Object> one(JdbcTemplate jdbc, String sql, Object... args) {
        List<Map<String, Object>> rows = list(jdbc, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<String> csv(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<String>();
        List<String> result = new ArrayList<String>();
        for (String item : value.split(",")) {
            String v = item.trim();
            if (!v.isEmpty()) result.add(v);
        }
        return result;
    }

    public static String join(Collection<?> values) {
        if (values == null) return "";
        List<String> parts = new ArrayList<String>();
        for (Object v : values) {
            if (v != null && !String.valueOf(v).trim().isEmpty()) parts.add(String.valueOf(v).trim());
        }
        return String.join(",", parts);
    }

    public static String joinValue(Object value) {
        if (value == null) return "";
        if (value instanceof Collection) return join((Collection<?>) value);
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> values = new ArrayList<Object>();
            for (int i = 0; i < length; i++) values.add(java.lang.reflect.Array.get(value, i));
            return join(values);
        }
        return join(csv(String.valueOf(value)));
    }
}
