package cn.pandora.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类（基于 Jackson）
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private JsonUtils() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("反序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("反序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("反序列化JSON列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object obj) {
        return MAPPER.convertValue(obj, Map.class);
    }

    public static <T> T mapToBean(Map<String, ?> map, Class<T> clazz) {
        return MAPPER.convertValue(map, clazz);
    }
}
