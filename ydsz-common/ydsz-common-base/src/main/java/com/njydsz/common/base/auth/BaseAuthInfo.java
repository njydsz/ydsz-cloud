package com.njydsz.common.base.auth;

import com.njydsz.common.util.auth.YdszAuthInfo;

/**
 * 认证上下文信息基类（Web/App 共享）
 *
 * <p>定义认证上下文的统一抽象，子类覆盖 {@link #getServiceTypeCode()} 返回具体的服务类型编码
 * （例如 "WEB" 或 "APP"），用于业务层区分请求来源。
 *
 * <p>本类继承自 {@link YdszAuthInfo}，具备完整的认证信息能力，包括：
 * <ul>
 *   <li>用户ID、登录账号、姓名等基础信息</li>
 *   <li>租户ID、公司ID、部门ID、项目ID、区域ID 等多维度隔离信息</li>
 *   <li>数据权限范围、可见列、可编辑列 等权限相关字段</li>
 *   <li>Token、刷新Token、过期时间等会话信息</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class WebAuthInfo extends BaseAuthInfo {
 *     &#64;Override
 *     public String getServiceTypeCode() {
 *         return "WEB";
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see YdszAuthInfo
 */
public abstract class BaseAuthInfo extends YdszAuthInfo {

    /**
     * 获取当前服务类型编码
     *
     * <p>用于标识当前请求所属的服务类型，区分 Web、App、API 等不同入口。
     *
     * @return 服务类型编码，例如 "WEB" / "APP" / "API"
     */
    @Override
    public abstract String getServiceTypeCode();
}
