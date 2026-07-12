package com.njydsz.pmis.common.app.auth;

import com.njydsz.pmis.common.auth.handler.AbstractAuthHandler;
import com.njydsz.pmis.common.util.auth.YdszAuthInfo;
import org.springframework.stereotype.Component;

/**
 * App 端认证信息处理器
 *
 * <p>通过模板方法模式，仅提供 {@link AppAuthInfo} 实例创建，
 * 解析逻辑由基类 {@link AbstractAuthHandler#getAuthInfo} 统一处理。
 *
 * <p><b>APP 与 WEB 差异：</b>App 端不依赖浏览器 Cookie，
 * 通常基于 {@code X-App-Token} 等自定义请求头进行认证，
 * 业务方可注入自定义 {@code AbstractAuthHandler} 子类以适配不同客户端协议。
 *
 * <p><b>线程安全性：</b>无状态 Bean，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see AbstractAuthHandler
 * @see AppAuthInfo
 */
@Component("appAuthHandler")
public class AppAuthHandler extends AbstractAuthHandler {

    /**
     * 创建 App 端认证信息实例
     *
     * @return 新的 {@link AppAuthInfo} 实例
     */
    @Override
    protected YdszAuthInfo createAuthInfo() {
        return new AppAuthInfo();
    }
}
