package com.njydsz.pmis.common.app.auth;

import com.njydsz.pmis.common.base.auth.BaseAuthInfo;
import com.njydsz.pmis.common.core.enums.ServiceType;

/**
 * App 端认证上下文信息
 *
 * <p>存储 App 请求处理过程中所需的认证上下文数据（如用户 ID、租户 ID、Token 等），
 * 通过 {@link com.njydsz.pmis.common.util.auth.RequestHolder} 在请求线程内传递。
 *
 * <p><b>服务类型：</b>固定返回 {@link ServiceType#APP_SERVICE} 的编码，
 * 与 Web 端、管理端的认证上下文作区分。
 *
 * <p><b>线程安全性：</b>依赖于 {@link RequestHolder} 的线程局部变量，实例本身不共享。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see com.njydsz.pmis.common.util.auth.RequestHolder
 */
public class AppAuthInfo extends BaseAuthInfo {

    /**
     * 获取服务类型编码
     *
     * @return 固定返回 {@link ServiceType#APP_SERVICE} 的编码
     */
    @Override
    public String getServiceTypeCode() {
        return ServiceType.APP_SERVICE.getCode();
    }
}
