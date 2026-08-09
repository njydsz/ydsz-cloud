package com.njydsz.common.util.auth;


import java.util.Map;
import java.util.Set;

import com.njydsz.common.domain.enums.DataScopeType;
import com.njydsz.common.domain.enums.IdentityType;

/**
 * 认证信息统一接口
 *
 * <p>定义了跨模块传递用户身份与权限上下文的标准契约，承载以下信息：
 * <ul>
 *   <li>基础身份：用户语言、唯一标识、身份类型、服务类型、访问令牌</li>
 *   <li>行级数据权限：租户ID、数据范围类型（tenant/group/company/dept/user/project/region）</li>
 *   <li>行级数据权限维度ID集合：公司ID集合、部门ID集合、项目ID集合、区域ID集合</li>
 *   <li>列级数据权限：表级可见列规则、可编辑列规则（基于角色/岗位）</li>
 * </ul>
 *
 * <p>实现类应通过 {@link com.njydsz.common.core.context.RequestContext} 写入上下文，供全链路下游读取。
 * 推荐使用实现类 {@link YdszAuthInfo}。
 *
 * @see YdszAuthInfo
 * @see com.njydsz.common.core.context.RequestContext
 * @see AuthInfoUtils
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthInfo {

    /**
     * 获取用户系统语言
     *
     * @return 语言码，如 {@code zh-CN}、{@code en-US}
     */
    String getUserLanguage();

    /**
     * 获取当前登录用户唯一标识
     *
     * @return 用户ID
     */
    String getUniqueId();

    /**
     * 获取身份类型
     *
     * @return 身份类型枚举，如公司用户、访客用户、ydsz用户
     * @see IdentityType
     */
    IdentityType getIdentityTypeEnum();

    /**
     * 获取服务类型编码
     *
     * @return 服务类型码，如 WEB_SERVICE、APP_SERVICE
     */
    String getServiceTypeCode();

    /**
     * 获取访问令牌
     *
     * @return AccessToken
     */
    String getAccessToken();

    /**
     * 获取数据权限范围类型
     *
     * <p>决定行级权限按哪个维度生效：
     * <ul>
     *   <li> TENANT：按租户维度过滤</li>
     *   <li> GROUP：按集团维度过滤（使用 companyIds）</li>
     *   <li> COMPANY：按公司维度过滤（使用 deptIds）</li>
     *   <li> DEPT：按部门维度过滤（使用 deptIds）</li>
     *   <li> USER：按用户维度过滤（使用 uniqueId）</li>
     *   <li> PROJECT：按项目维度过滤（使用 projectIds）</li>
     *   <li> REGION：按区域维度过滤（使用 regionIds）</li>
     * </ul>
     *
     * @return 数据范围类型枚举
     * @see DataScopeType
     */
    DataScopeType getDataScope();

    /**
     * 获取租户ID
     *
     * <p>用于 TENANT 范围类型的行级权限过滤。
     *
     * @return 租户ID
     */
    String getTenantId();

    /**
     * 获取用户设备唯一标识
     *
     * @return 设备唯一ID
     */
    String getDistinctId();

    /**
     * 获取请求来源标识
     *
     * <p>用于标识请求的来源渠道。
     *
     * @return 请求来源
     */
    String getRequestSource();

    /**
     * 获取列级权限：表级可见列规则
     *
     * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许查看的列名集合（小写）。
     *
     * <p>示例：
     * <pre>{@code
     * {
     *   "sys_user" -> {"id", "name", "email"},
     *   "sys_role" -> {"id", "role_name"}
     * }
     * }</pre>
     *
     * <p>SQL 拦截器会据此过滤 SELECT 语句中的返回列。
     *
     * @return 表名到可见列集合的映射；不允许返回 null，无规则时返回空 Map
     */
    Map<String, Set<String>> getVisibleColumnsByTable();

    /**
     * 获取列级权限：表级可编辑列规则
     *
     * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许写入的列名集合（小写）。
     *
     * <p>示例：
     * <pre>{@code
     * {
     *   "sys_user" -> {"name", "email", "phone"},
     *   "sys_role" -> {"role_name", "description"}
     * }
     * }</pre>
     *
     * <p>SQL 拦截器会据此过滤 INSERT/UPDATE 语句中的写入列；若无任何可编辑列则抛出异常阻断写入。
     *
     * @return 表名到可编辑列集合的映射；不允许返回 null，无规则时返回空 Map
     */
    Map<String, Set<String>> getEditableColumnsByTable();
}
