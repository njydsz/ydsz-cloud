package com.njydsz.common.util.auth;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 认证信息快捷读取工具类。
 *
 * <p>封装 {@link RequestContext#getAuthInfo()} 的常用读取逻辑，提供空值安全的快捷方法：
 * <ul>
 *   <li>基础身份：用户语言、访问令牌、用户ID、身份类型、服务类型</li>
 *   <li>行级数据权限：数据范围类型、租户ID</li>
 *   <li>行级数据权限维度ID集合：公司ID、部门ID、项目ID、区域ID</li>
 *   <li>列级数据权限：表级可见列规则、表级可编辑列规则</li>
 * </ul>
 *
 * <p>所有方法均做空值判断，底层 {@link AuthInfo} 为 null 时返回安全默认值：
 * <ul>
 *   <li>返回类型为 Set：返回 {@link Collections#emptySet()}</li>
 *   <li>返回类型为 Map：返回 {@link Collections#emptyMap()}</li>
 *   <li>返回类型为 String：返回 null</li>
 * </ul>
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>Feign 远程调用：透传数据权限 header</li>
 *   <li>SQL 拦截器：读取上下文构建过滤条件</li>
 *   <li>业务代码：判断当前用户权限范围</li>
 * </ul>
 *
 * @see AuthInfo
 * @see YdszAuthInfo
 * @see RequestContext
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class AuthInfoUtils {

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private AuthInfoUtils() {
    }

    /**
     * 获取当前线程的认证信息。
     *
     * <p>使用 {@code instanceof} 类型安全保护，避免 RequestContext 中存放了非 AuthInfo 类型时抛 ClassCastException。
     *
     * @return AuthInfo 实例；若未写入或类型不匹配则返回 null
     * @see RequestContext#get(String)
      */
    public static AuthInfo getAuthInfo() {
        Object value = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
        return value instanceof AuthInfo auth ? auth : null;
    }

    /**
     * 获取当前用户语言。
     *
     * @return 语言码；无上下文时返回 null
      */
    public static String getUserLanguage() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getUserLanguage() : null;
    }

    /**
     * 获取访问令牌。
     *
     * @return AccessToken；无上下文时返回 null
      */
    public static String getAccessToken() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getAccessToken() : null;
    }

    /**
     * 获取当前登录用户唯一标识。
     *
     * @return 用户ID；无上下文时返回 null
      */
    public static String getUniqueId() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getUniqueId() : null;
    }

    /**
     * 获取身份类型编码。
     *
     * @return 身份类型编码字符串；无上下文时返回 null
     * @see com.njydsz.common.domain.constant.DataScopeConstants
      */
    public static String getIdentityType() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getIdentityType() : null;
    }

    /**
     * 获取服务类型编码。
     *
     * @return 服务类型码；无上下文时返回 null
      */
    public static String getServiceTypeCode() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getServiceTypeCode() : null;
    }

    /**
     * 获取数据权限范围类型编码。
     *
     * <p>决定行级权限按哪个维度（tenant/group/company/dept/user/project/region）生效。
     *
     * @return 数据范围类型编码字符串；无上下文时返回 null
     * @see com.njydsz.common.domain.constant.DataScopeConstants
     */
    public static String getDataScope() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getDataScope() : null;
    }

    /**
     * 获取租户ID。
     *
     * <p>用于 TENANT 范围类型的行级权限过滤。
     *
     * @return 租户ID；无上下文时返回 null
      */
    public static String getTenantId() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getTenantId() : null;
    }

    /**
     * 获取设备唯一标识。
     *
     * @return 设备ID；无上下文时返回 null
      */
    public static String getDistinctId() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getDistinctId() : null;
    }

    /**
     * 获取请求来源标识。
     *
     * @return 请求来源；无上下文时返回 null
      */
    public static String getRequestSource() {
        AuthInfo auth = getAuthInfo();
        return auth != null ? auth.getRequestSource() : null;
    }

    /**
     * 获取当前用户可访问的公司ID集合（GROUP 范围权限）。
     *
     * <p>当 {@link #getDataScope()} 返回 {@code "group"} 时，SQL 拦截器使用此集合过滤 company_id。
     *
     * @return 公司ID集合；无上下文或未设置时返回空 Set
     * @author ydsz-team
     * @since 1.0.0
     *
     */
    public static Set<String> getHasPermissionCompanyIds() {
        YdszAuthInfo auth = getYdszAuthInfo();
        return (auth != null && auth.getHasPermissionCompanyIds() != null)
                ? auth.getHasPermissionCompanyIds() : Collections.emptySet();
    }

    /**
     * 获取当前用户可访问的部门ID集合（COMPANY/DEPT 范围权限）。
     *
     * <p>当 {@link #getDataScope()} 返回 {@code "company"} 或 {@code "dept"} 时，SQL 拦截器使用此集合过滤 dept_id。
     *
     * @return 部门ID集合；无上下文或未设置时返回空 Set
     * @author ydsz-team
     * @since 1.0.0
     *
     */
    public static Set<String> getHasPermissionDeptIds() {
        YdszAuthInfo auth = getYdszAuthInfo();
        return (auth != null && auth.getHasPermissionDeptIds() != null)
                ? auth.getHasPermissionDeptIds() : Collections.emptySet();
    }

    /**
     * 获取当前用户可访问的项目ID集合（PROJECT 范围权限）。
     *
     * <p>当 {@link #getDataScope()} 返回 {@code "project"} 时，SQL 拦截器使用此集合过滤 project_id。
     *
     * @return 项目ID集合；无上下文或未设置时返回空 Set
     * @author ydsz-team
     * @since 1.0.0
     *
     */
    public static Set<String> getHasPermissionProjectIds() {
        YdszAuthInfo auth = getYdszAuthInfo();
        return (auth != null && auth.getHasPermissionProjectIds() != null)
                ? auth.getHasPermissionProjectIds() : Collections.emptySet();
    }

    /**
     * 获取当前用户可访问的区域ID集合（REGION 范围权限）。
     *
     * <p>当 {@link #getDataScope()} 返回 {@code "region"} 时，SQL 拦截器使用此集合过滤 region_id。
     *
     * @return 区域ID集合；无上下文或未设置时返回空 Set
     * @author ydsz-team
     * @since 1.0.0
     *
     */
    public static Set<String> getHasPermissionRegionIds() {
        YdszAuthInfo auth = getYdszAuthInfo();
        return (auth != null && auth.getHasPermissionRegionIds() != null)
                ? auth.getHasPermissionRegionIds() : Collections.emptySet();
    }

    /**
     * 获取公司级认证信息。
     *
     * <p>类型转换为 YdszAuthInfo，以访问行级权限维度ID集合（companyIds/deptIds/projectIds/regionIds）。
     * 使用 {@code instanceof} 类型安全保护，避免 ClassCastException。
     *
     * @return YdszAuthInfo；若非该类型或未写入则返回 null
     * @see YdszAuthInfo
     * @author ydsz-team
     * @since 1.0.0
     *
     */
    public static YdszAuthInfo getYdszAuthInfo() {
        Object value = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
        return value instanceof YdszAuthInfo auth ? auth : null;
    }

    /**
     * 获取列级权限：表级可见列规则。
     *
     * <p>格式：{@code tableName -> Set<columnName>}，表名和列名均小写。
     *
     * <p>典型使用：Feign 远程调用时将此规则序列化为 header 透传给下游服务。
     *
     * @return 表名到可见列集合的映射；无上下文时返回空 Map
     * @see AuthInfo#getVisibleColumnsByTable()
      */
    public static Map<String, Set<String>> getVisibleColumnsByTable() {
        AuthInfo auth = getAuthInfo();
        return auth != null && auth.getVisibleColumnsByTable() != null
                ? auth.getVisibleColumnsByTable() : Collections.emptyMap();
    }

    /**
     * 获取列级权限：表级可编辑列规则。
     *
     * <p>格式：{@code tableName -> Set<columnName>}，表名和列名均小写。
     *
     * <p>典型使用：Feign 远程调用时将此规则序列化为 header 透传给下游服务。
     *
     * @return 表名到可编辑列集合的映射；无上下文时返回空 Map
     * @see AuthInfo#getEditableColumnsByTable()
      */
    public static Map<String, Set<String>> getEditableColumnsByTable() {
        AuthInfo auth = getAuthInfo();
        return auth != null && auth.getEditableColumnsByTable() != null
                ? auth.getEditableColumnsByTable() : Collections.emptyMap();
    }

    /**
     * 按 claim 名从认证上下文获取值（动态字段提取）。
     *
     * <p>支持以下 claim 名：
     * <ul>
     *   <li>{@code tenantId} → {@link AuthInfo#getTenantId()}</li>
     *   <li>{@code uniqueId} / {@code userId} → {@link AuthInfo#getUniqueId()}</li>
     *   <li>{@code companyIds} → 逗号拼接的公司 ID 集合</li>
     *   <li>{@code deptIds} → 逗号拼接的部门 ID 集合</li>
     *   <li>{@code projectIds} → 逗号拼接的项目 ID 集合</li>
     *   <li>{@code regionIds} → 逗号拼接的区域 ID 集合</li>
     * </ul>
     *
     * <p>未匹配的 claim 名返回 null。
     *
     * @param claim claim 名
     * @return 值，无上下文返回 null
     */
    public static String getClaim(String claim) {
        if (claim == null || claim.isEmpty()) {
            return null;
        }
        AuthInfo auth = getAuthInfo();
        if (auth == null) {
            return null;
        }
        switch (claim) {
            case "tenantId":
                return auth.getTenantId();
            case "uniqueId":
            case "userId":
                return auth.getUniqueId();
            case "companyIds":
                Set<String> companyIds = getHasPermissionCompanyIds();
                return companyIds.isEmpty() ? null : String.join(",", companyIds);
            case "deptIds":
                Set<String> deptIds = getHasPermissionDeptIds();
                return deptIds.isEmpty() ? null : String.join(",", deptIds);
            case "projectIds":
                Set<String> projectIds = getHasPermissionProjectIds();
                return projectIds.isEmpty() ? null : String.join(",", projectIds);
            case "regionIds":
                Set<String> regionIds = getHasPermissionRegionIds();
                return regionIds.isEmpty() ? null : String.join(",", regionIds);
            default:
                log.debug("未识别的 claim 名: {}，支持的 claim: tenantId, uniqueId, userId, "
                        + "companyIds, deptIds, projectIds, regionIds", claim);
                return null;
        }
    }
}
