package com.njydsz.pmis.common.app.auth;

import com.njydsz.pmis.common.base.auth.BaseAuthInfo;

/**
 * App 端认证上下文信息
 *
 * <p>存储 App 请求处理过程中所需的认证上下文数据。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AppAuthInfo extends BaseAuthInfo {

    @Override
    public String getServiceTypeCode() {
        return "APP";
    }
}
