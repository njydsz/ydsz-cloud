package com.njydsz.common.auth.i18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 权限消息国际化工具类。
 *
 * <p>根据用户语言偏好返回对应的权限拒绝消息文案。
 * 支持 zh-CN（中文，默认）和 en-US（英文）。
 *
 * <p>使用方式：
 * <pre>{@code
 * String message = PermissionMessageResolver.resolve("permission.denied", "zh-CN");
 * }</pre>
 *
 * @since 1.1.0

 */
public final class PermissionMessageResolver {

    /**
     * 中文消息映射
     */
    private static final Map<String, String> ZH_CN = new HashMap<>();

    /**
     * 英文消息映射
     */
    private static final Map<String, String> EN_US = new HashMap<>();

    static {
        ZH_CN.put("permission.denied", "权限不足");
        ZH_CN.put("permission.denied.menu", "菜单权限校验失败");
        ZH_CN.put("permission.denied.button", "按钮权限校验失败");
        ZH_CN.put("permission.denied.api", "接口权限校验失败");
        ZH_CN.put("permission.denied.data", "数据权限校验失败");
        ZH_CN.put("permission.denied.column", "列权限校验失败");
        ZH_CN.put("permission.denied.role", "角色权限校验失败");
        ZH_CN.put("permission.suggestion", "请联系管理员为您授予权限");
        ZH_CN.put("permission.unauthorized", "用户未登录");
        ZH_CN.put("permission.token.expired", "访问令牌已过期，请重新登录");
        ZH_CN.put("permission.token.missing", "缺少访问令牌");
        ZH_CN.put("permission.token.invalid", "无效的访问令牌");

        EN_US.put("permission.denied", "Permission denied");
        EN_US.put("permission.denied.menu", "Menu permission check failed");
        EN_US.put("permission.denied.button", "Button permission check failed");
        EN_US.put("permission.denied.api", "API permission check failed");
        EN_US.put("permission.denied.data", "Data permission check failed");
        EN_US.put("permission.denied.column", "Column permission check failed");
        EN_US.put("permission.denied.role", "Role permission check failed");
        EN_US.put("permission.suggestion", "Please contact administrator to grant permissions");
        EN_US.put("permission.unauthorized", "User not logged in");
        EN_US.put("permission.token.expired", "Access token expired, please login again");
        EN_US.put("permission.token.missing", "Missing access token");
        EN_US.put("permission.token.invalid", "Invalid access token");
    }

    private PermissionMessageResolver() {
    }

    /**
     * 解析国际化消息。
     *
     * @param key     消息键
     * @param language 语言偏好（如 "zh-CN", "en-US"）
     * @return 对应语言的文案，未匹配时返回中文默认值
     */
    public static String resolve(String key, String language) {
        if (language != null && language.toLowerCase().startsWith("en")) {
            return EN_US.getOrDefault(key, ZH_CN.getOrDefault(key, key));
        }
        return ZH_CN.getOrDefault(key, key);
    }

    /**
     * 解析国际化消息，带参数替换。
     *
     * @param key      消息键
     * @param language 语言偏好
     * @param args     替换参数
     * @return 替换参数后的文案
     */
    public static String resolve(String key, String language, Object... args) {
        String template = resolve(key, language);
        if (args == null || args.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
