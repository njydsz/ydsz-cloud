package com.remisoft.common.web.auth;

import org.springframework.stereotype.Component;

import com.remisoft.common.auth.handler.AbstractAuthHandler;
import com.remisoft.common.util.auth.RemiAuthInfo;

/**
 * Web 端认证信息处理器
 *
 * <p>通过模板方法模式，仅提供 {@link WebAuthInfo} 实例创建，
 * 解析逻辑由基类 {@link AbstractAuthHandler#getAuthInfo} 统一处理。
 *
 * @see AbstractAuthHandler
 * @see WebAuthInfo
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component("webAuthHandler")
public class WebAuthHandler extends AbstractAuthHandler {

    @Override
    protected RemiAuthInfo createAuthInfo() {
        return new WebAuthInfo();
    }
}
