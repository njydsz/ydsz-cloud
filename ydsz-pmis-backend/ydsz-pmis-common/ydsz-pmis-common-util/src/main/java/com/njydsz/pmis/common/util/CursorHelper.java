package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.StringTokenizer;

/**
 * 游标编解码工具（P2-8 深翻优化）
 *
 * <p>将排序字段值 + 主键 ID 编码为 Base64 opaque 字符串，供游标分页使用。
 * 前端不应解析 cursor 内容，仅原样回传。
 *
 * <p>格式：{@code Base64("sortValue|id")}
 *
 * @author ydsz-dpmis-team
 * @since 1.0.0
 */
public final class CursorHelper {

    /** 日期时间格式（ISO 格式，便于排序比较） */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorHelper() {
    }

    /**
     * 编码游标（LocalDateTime + id）
     *
     * @param sortValue 排序字段值（通常为 created_at）
     * @param id        主键 ID（雪花算法字符串）
     * @return Base64 编码的 cursor 字符串
     */
    public static String encode(LocalDateTime sortValue, String id) {
        String raw = sortValue.format(FORMATTER) + "|" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码游标，提取排序值和 ID
     *
     * @param cursor 游标字符串
     * @return [0] = LocalDateTime, [1] = String id
     * @throws IllegalArgumentException 游标格式非法时抛出
     */
    public static Object[] decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = DECODER.decode(cursor);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            StringTokenizer st = new StringTokenizer(raw, "|");
            if (st.countTokens() != 2) {
                throw new IllegalArgumentException("cursor 格式非法: token 数量 != 2");
            }
            LocalDateTime sortValue = LocalDateTime.parse(st.nextToken(), FORMATTER);
            String id = st.nextToken();
            return new Object[]{sortValue, id};
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor 解码失败: " + e.getMessage(), e);
        }
    }
}
