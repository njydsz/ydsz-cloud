package com.njydsz.pmis.common.security;

import java.util.Map;
import java.util.Set;

/**
 * 认证信息统一接口
 *
 * <p>定义了跨模块传递用户身份与权限上下文的标准契约。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AuthInfo {

    String getUserLanguage();

    String getUserId();

    String getAccessToken();

    String getTenantId();

    String getIdentityType();

    String getServiceType();

    Set<String> getCompanyIds();

    Set<String> getDeptIds();

    Set<String> getProjectIds();

    Set<String> getRegionIds();

    Map<String, String> getVisibleColumns();

    Map<String, String> getEditableColumns();
}
