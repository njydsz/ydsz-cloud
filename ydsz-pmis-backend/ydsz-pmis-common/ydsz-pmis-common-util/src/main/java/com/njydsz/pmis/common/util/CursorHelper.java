package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.json.type.YdszJsonType;
import com.njydsz.pmis.common.json.YdszJson;

/**
 * 游标分页工具类。
 *
 * <p>提供游标的创建、解析和验证功能，用于基于游标的分页查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CursorHelper {

    // JsonUtils 作为 JSON 引擎（底层 YdszJson）
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private CursorHelper() {
    }

    // ==================== String-based cursor (generic) ====================

    /**
     * 创建游标。
     *
     * @param sortValue 排序值（通常是上一页最后一条记录的排序字段值）
     * @param id        记录 ID（用于排序值相同时的二级排序）
     * @return Base64 编码的游标字符串
     */
    public static String create(String sortValue, String id) {
        Map<String, String> cursor = new HashMap<>();
        cursor.put("sv", sortValue);
        cursor.put("id", id);
        try {
            String json = YdszJson.toJson(cursor);
            return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("创建游标失败", e);
        }
    }

    /**
     * 解析游标。
     *
     * @param cursor Base64 编码的游标字符串
     * @return 包含 sortValue 和 id 的 Map
     */
    public static Map<String, String> parse(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return YdszJson.toObject(json, new YdszJsonType<Map<String, String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取游标中的排序值。
     *
     * @param cursor Base64 编码的游标字符串
     * @return 排序值，游标无效时返回 null
     */
    public static String getSortValue(String cursor) {
        Map<String, String> parsed = parse(cursor);
        return parsed != null ? parsed.get("sv") : null;
    }

    /**
     * 获取游标中的记录 ID。
     *
     * @param cursor Base64 编码的游标字符串
     * @return 记录 ID，游标无效时返回 null
     */
    public static String getId(String cursor) {
        Map<String, String> parsed = parse(cursor);
        return parsed != null ? parsed.get("id") : null;
    }

    /**
     * 验证游标是否有效。
     *
     * @param cursor Base64 编码的游标字符串
     * @return true 表示游标有效
     */
    public static boolean isValid(String cursor) {
        return parse(cursor) != null;
    }

    // ==================== LocalDateTime-based cursor (for keyset pagination) ====================

    /**
     * 编码游标（基于 LocalDateTime 排序值）。
     *
     * <p>用于 keyset pagination 场景，将排序时间与记录 ID 编码为 Base64 游标。
     *
     * @param sortValue 排序时间值
     * @param id        记录 ID
     * @return Base64 编码的游标字符串
     */
    public static String encode(LocalDateTime sortValue, String id) {
        Map<String, String> cursor = new HashMap<>();
        cursor.put("sv", sortValue != null ? sortValue.format(DT_FORMATTER) : null);
        cursor.put("id", id);
        try {
            String json = YdszJson.toJson(cursor);
            return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("编码游标失败", e);
        }
    }

    /**
     * 解码游标（返回 Object 数组，兼容 keyset pagination）。
     *
     * <p>返回数组的第一个元素为 {@link LocalDateTime}（排序值），
     * 第二个元素为 {@link String}（记录 ID）。
     *
     * @param cursor Base64 编码的游标字符串
     * @return 包含 [LocalDateTime, String] 的数组，游标无效时返回 null
     */
    public static Object[] decode(String cursor) {
        Map<String, String> parsed = parse(cursor);
        if (parsed == null) {
            return null;
        }
        try {
            String sv = parsed.get("sv");
            String id = parsed.get("id");
            LocalDateTime sortValue = sv != null ? LocalDateTime.parse(sv, DT_FORMATTER) : null;
            return new Object[]{sortValue, id};
        } catch (Exception e) {
            return null;
        }
    }
}
