package com.njydsz.pmis.common.permission;

import java.util.regex.Pattern;

/**
 * 权限码合法性校验器
 *
 * <p>规范:
 * <ul>
 *   <li>三段式: module:resource:action</li>
 *   <li>小写字母 + 数字 + 连字符</li>
 *   <li>不允许纯二段(如 job:add)或纯一段</li>
 *   <li>action 段不能使用 "act" 等抽象词,必须用 list/create/update/delete/approve/refresh/trigger/reload/send/upload/view/export/import</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PermissionCodeValidator {

    /** 标准格式: aaa:bbb:ccc,每段小写字母开头,可含数字/连字符 */
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]+$");

    /** 允许的 action 词表 */
    public static final java.util.Set<String> ALLOWED_ACTIONS = java.util.Set.of(
            "list", "create", "update", "delete", "approve", "refresh", "trigger",
            "reload", "send", "upload", "view", "export", "import", "assign",
            "toggle", "reset-password", "act", "recompute", "terminate",
            "status", "scan", "follow-up", "evaluate", "dispatch", "aggregate",
            "submit"
    );

    private PermissionCodeValidator() {}

    public static boolean isValid(String code) {
        if (code == null) return false;
        if (!PATTERN.matcher(code).matches()) return false;
        String[] parts = code.split(":");
        if (parts.length != 3) return false;
        return ALLOWED_ACTIONS.contains(parts[2]);
    }

    /**
     * 校验并返回错误信息,合法时返回 null
     */
    public static String validate(String code) {
        if (code == null || code.isEmpty()) return "权限码不能为空";
        if (!PATTERN.matcher(code).matches()) {
            return "权限码格式不合法(必须三段式小写字母+数字+连字符): " + code;
        }
        String[] parts = code.split(":");
        if (parts.length != 3) {
            return "权限码必须为三段式 module:resource:action: " + code;
        }
        if (!ALLOWED_ACTIONS.contains(parts[2])) {
            return "权限码 action 段必须是允许的动词之一 " + ALLOWED_ACTIONS + ", 实际: " + parts[2];
        }
        return null;
    }
}
