package com.njydsz.common.jdbc.monitor;

import java.util.regex.Pattern;

/**
 * SQL 指纹归一化工具
 *
 * <p>将 SQL 语句中的参数值、IN 列表等替换为占位符，生成稳定的指纹用于 Micrometer tag，
 * 避免高基数标签导致 Prometheus 内存爆炸。
 *
 * <p>归一化规则：
 * <ul>
 *   <li>字符串字面量 {@code 'xxx'} → {@code ?}</li>
 *   <li>数字字面量 {@code 123} → {@code ?}</li>
 *   <li>IN 列表 {@code IN (1,2,3)} → {@code IN (?)}</li>
 *   <li>多空格折叠为单空格</li>
 *   <li>超长指纹截断到 200 字符</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * fingerprint("SELECT * FROM users WHERE id = 1 AND name = 'alice'")
 * → "SELECT * FROM users WHERE id = ? AND name = ?"
 *
 * fingerprint("SELECT * FROM orders WHERE id IN (1,2,3,4,5)")
 * → "SELECT * FROM orders WHERE id IN (?)"
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SqlFingerprint {

    /** 字符串字面量：'xxx' 或 "xxx" */
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"");

    /** IN 列表：IN ( ?, ?, ? ) → IN (?) */
    private static final Pattern IN_LIST = Pattern.compile("IN\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)*\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** 数字字面量（不含 IN 列表内已替换的 ?） */
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");

    /** 多空格折叠 */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /** 指纹最大长度 */
    private static final int MAX_FINGERPRINT_LENGTH = 200;

    /**
     * 私有构造方法，工具类禁止实例化。
     */
    private SqlFingerprint() {
    }

    /**
     * 生成 SQL 指纹
     *
     * @param sql 原始 SQL 语句
     * @return 归一化后的 SQL 指纹；输入为 null 时返回 "unknown"
     */
    public static String fingerprint(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "unknown";
        }

        String result = sql;

        // 1. 替换字符串字面量
        result = STRING_LITERAL.matcher(result).replaceAll("?");

        // 2. 替换数字字面量
        result = NUMBER_LITERAL.matcher(result).replaceAll("?");

        // 3. 折叠 IN 列表
        result = IN_LIST.matcher(result).replaceAll("IN (?)");

        // 4. 折叠多空格
        result = MULTI_SPACE.matcher(result).replaceAll(" ");

        // 5. 去除首尾空格
        result = result.trim();

        // 6. 截断超长指纹
        if (result.length() > MAX_FINGERPRINT_LENGTH) {
            result = result.substring(0, MAX_FINGERPRINT_LENGTH) + "...";
        }

        return result;
    }
}
