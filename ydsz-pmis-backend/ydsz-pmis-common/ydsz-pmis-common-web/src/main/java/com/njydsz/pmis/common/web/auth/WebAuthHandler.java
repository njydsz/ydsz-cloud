package com.njydsz.pmis.common.web.auth;

import com.njydsz.pmis.common.auth.handler.AbstractAuthHandler;
import com.njydsz.pmis.common.util.auth.YdszAuthInfo;
import org.springframework.stereotype.Component;

/**
 * Web 端认证信息处理器
 *
 * <p>通过模板方法模式，仅提供 {@link WebAuthInfo} 实例创建，
 * 解析逻辑由基类 {@link AbstractAuthHandler#getAuthInfo} 统一处理。
 *
 * @see AbstractAuthHandler
 * @see WebAuthInfo
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Component("webAuthHandler")
public class WebAuthHandler extends AbstractAuthHandler {

    @Override
    protected YdszAuthInfo createAuthInfo() {
        return new WebAuthInfo();
    }
}
