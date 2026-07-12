package com.njydsz.pmis.common.context;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 认证信息快捷读取工具类
 *
 * <p>封装 {@link RequestHolder#getAuthInfo()} 的常用读取逻辑，提供空值安全的快捷方法。
 * 放置在 common-core 模块以避免循环依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AuthInfoUtils {

    private AuthInfoUtils() {
    }

    public static AuthInfo getAuthInfo() {
        return RequestHolder.getAuthInfo();
    }

    public static String getUserId() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getUserId() : null;
    }

    public static String getUserLanguage() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getUserLanguage() : null;
    }

    public static String getAccessToken() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getAccessToken() : null;
    }

    public static String getTenantId() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getTenantId() : null;
    }

    public static String getIdentityType() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getIdentityType() : null;
    }

    public static String getServiceType() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getServiceType() : null;
    }

    public static Set<String> getCompanyIds() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getCompanyIds() : Collections.emptySet();
    }

    public static Set<String> getDeptIds() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getDeptIds() : Collections.emptySet();
    }

    public static Set<String> getProjectIds() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getProjectIds() : Collections.emptySet();
    }

    public static Set<String> getRegionIds() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getRegionIds() : Collections.emptySet();
    }

    public static Map<String, String> getVisibleColumns() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getVisibleColumns() : Collections.emptyMap();
    }

    public static Map<String, String> getEditableColumns() {
        AuthInfo info = getAuthInfo();
        return info != null ? info.getEditableColumns() : Collections.emptyMap();
    }
}
