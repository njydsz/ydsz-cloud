package com.njydsz.common.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.YdszJsonType;

/**
 * 游标分页工具类。
 *
 * <p>提供游标的创建、解析和验证功能，用于基于游标的分页查询（Cursor-based Pagination）。
 * 相比传统 offset/limit 分页，游标分页在大数据集下性能更优，避免了深度翻页时的性能退化。
 *
 * <h2>核心能力</h2>
 * <ul>
 *   <li>String 游标：通用场景，排序值为字符串（如 ID、名称等）</li>
 *   <li>LocalDateTime 游标：Keyset Pagination 场景，排序值为时间戳</li>
 *   <li>Base64 URL Safe 编码：游标以 Base64 URL 编码字符串形式传递，前端友好</li>
 * </ul>
 *
 * <h2>游标结构</h2>
 * <p>游标内部为 JSON 对象，包含两个字段：
 * <ul>
 *   <li>{@code sv}（sortValue）：排序字段值，用于定位分页起点</li>
 *   <li>{@code id}：记录唯一标识，用于排序值相同时的二级排序（tie-breaker）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建游标（上一页最后一条记录的排序值和 ID）
 * String cursor = CursorHelper.create("2024-01-15T10:30:00", "abc123");
 *
 * // 解析游标
 * Map<String, String> parsed = CursorHelper.parse(cursor);
 * String sortValue = CursorHelper.getSortValue(cursor);
 * String id = CursorHelper.getId(cursor);
 *
 * // 验证游标有效性
 * if (CursorHelper.isValid(cursor)) { ... }
 *
 * // Keyset Pagination（基于 LocalDateTime）
 * String cursor2 = CursorHelper.encode(LocalDateTime.now(), "abc123");
 * Object[] decoded = CursorHelper.decode(cursor2); // [LocalDateTime, String]
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CursorHelper {

    /**
     * LocalDateTime 格式化器，使用 ISO 8601 格式（如 {@code 2024-01-15T10:30:00}）。
     */
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 私有构造器，工具类不允许实例化。
     */
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
        try {
            String json = YdszJson.toJson(Map.of("sv", sortValue, "id", id));
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
        try {
            String json = YdszJson.toJson(Map.of("sv", sortValue != null ? sortValue.format(DT_FORMATTER) : "", "id", id));
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
