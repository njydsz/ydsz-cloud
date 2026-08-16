package com.njydsz.common.core.model;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 认证信息统一接口
 *
 * <p>定义了跨模块传递用户身份与权限上下文的标准契约，承载以下信息：
 * <ul>
 *   <li>基础身份：用户语言、唯一标识、身份类型、服务类型、访问令牌</li>
 *   <li>行级数据权限：租户ID、数据范围类型（tenant/group/company/dept/user/project/region）</li>
 *   <li>列级数据权限：表级可见列规则、可编辑列规则（基于角色/岗位）</li>
 * </ul>
 *
 * <p>本接口定义于 core 模块（L2 基础设施层），供所有层级引用。
 * 实现类由 common-auth 提供（{@code com.njydsz.common.auth.model.YdszAuthInfo}）。</p>
 *
 * @author ydsz-team
 * @since 2.0.0
 */
public interface AuthInfo {

    /**
     * 获取用户系统语言
     *
     * @return 语言码，如 {@code zh-CN}、{@code en-US}
     */
    @Nullable
    String getUserLanguage();

    /**
     * 获取当前登录用户唯一标识
     *
     * @return 用户ID
     */
    @Nullable
    String getUniqueId();

    /**
     * 获取身份类型
     *
     * @return 身份类型编码，如 "company"、"visitor"、"ydszsoft"
     */
    @Nullable
    String getIdentityType();

    /**
     * 获取服务类型编码
     *
     * @return 服务类型码，如 "webService"、"appService"
     */
    @Nullable
    String getServiceTypeCode();

    /**
     * 获取访问令牌
     *
     * @return AccessToken
     */
    @Nullable
    String getAccessToken();

    /**
     * 获取数据权限范围类型
     *
     * <p>决定行级权限按哪个维度生效：
     * <ul>
     *   <li>tenant：按租户维度过滤</li>
     *   <li>group：按集团维度过滤</li>
     *   <li>company：按公司维度过滤</li>
     *   <li>dept：按部门维度过滤</li>
     *   <li>user：按用户维度过滤</li>
     *   <li>project：按项目维度过滤</li>
     *   <li>region：按区域维度过滤</li>
     * </ul>
     *
     * @return 数据范围类型编码
     */
    @Nullable
    String getDataScope();

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    @Nullable
    String getTenantId();

    /**
     * 获取用户设备唯一标识
     *
     * @return 设备唯一ID
     */
    @Nullable
    String getDistinctId();

    /**
     * 获取请求来源标识
     *
     * @return 请求来源
     */
    @Nullable
    String getRequestSource();

    /**
     * 获取列级权限：表级可见列规则
     *
     * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许查看的列名集合（小写）。
     *
     * @return 表名到可见列集合的映射；不允许返回 null，无规则时返回空 Map
     */
    @Nonnull
    Map<String, Set<String>> getVisibleColumnsByTable();

    /**
     * 获取列级权限：表级可编辑列规则
     *
     * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许写入的列名集合（小写）。
     *
     * @return 表名到可编辑列集合的映射；不允许返回 null，无规则时返回空 Map
     */
    @Nonnull
    Map<String, Set<String>> getEditableColumnsByTable();
}
