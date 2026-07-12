package com.njydsz.pmis.common.web.auth;

import com.njydsz.pmis.common.base.auth.BaseAuthInfo;
import com.njydsz.pmis.common.core.enums.ServiceType;

/**
 * Web 端认证上下文信息
 *
 * <p>继承 {@link BaseAuthInfo}，为 Web 端（管理端）提供服务类型标识。
 * 解析逻辑由基类 {@link BaseAuthInfo} 统一处理。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseAuthInfo
 * @see com.njydsz.pmis.common.core.enums.ServiceType#WEB_SERVICE
 */
public class WebAuthInfo extends BaseAuthInfo {

    /**
     * 获取服务类型编码
     *
     * @return Web 端服务类型编码
     */
    @Override
    public String getServiceTypeCode() {
        return ServiceType.WEB_SERVICE.getCode();
    }
}
