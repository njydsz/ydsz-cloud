package com.njydsz.pmis.common.util.auth;

import com.njydsz.pmis.common.core.enums.ServiceType;
import com.njydsz.pmis.common.core.enums.DataScopeType;
import com.njydsz.pmis.common.core.enums.DataScopeType;
import com.njydsz.pmis.common.core.enums.IdentityType;
import lombok.Data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import com.njydsz.pmis.common.app.auth.AppAuthInfo;
import com.njydsz.pmis.common.web.auth.WebAuthInfo;

/**
 * ydsz系统统一认证上下文信息抽象基类。
 *
 * <p>承载请求维度的全量身份与权限数据，在 {@link com.njydsz.pmis.common.web.filter.WebAuthFilter}
 * / {@link com.njydsz.pmis.common.app.filter.AppAuthFilter} 解析请求头后写入 {@link RequestHolder}，
 * 供下游链路（SQL 拦截器、Feign 透传、数据权限切面等）随时获取。
 *
 * <p>设计说明：
 * <ul>
 *   <li>身份类型固定为 {@link IdentityType#COMPANY}（公司级），不支持继承扩展</li>
 *   <li>服务类型由子类通过 {@link #getServiceTypeCode()} 实现区分（WEB_SERVICE / APP_SERVICE）</li>
 *   <li>所有集合类型字段使用不可变空集合初始化，防止 NPE</li>
 *   <li>行级权限维度（companyIds / deptIds / projectIds / regionIds）支持多值 CSV 格式</li>
 *   <li>列权限（visibleColumnsByTable / editableColumnsByTable）格式为 {@code tableName:col1,col2;tableName2:col3}</li>
 * </ul>
 *
 * <p>与请求头的对应关系：
 * <table border="1">
 *   <tr><th>字段</th><th>对应请求头</th></tr>
 *   <tr><td>userLanguage</td><td>X-User-Language</td></tr>
 *   <tr><td>uniqueId</td><td>X-Unique-Id</td></tr>
 *   <tr><td>accessToken</td><td>X-Access-Token</td></tr>
 *   <tr><td>dataScope</td><td>X-Data-Scope</td></tr>
 *   <tr><td>hasPermissionCompanyIds</td><td>X-Company-Ids</td></tr>
 *   <tr><td>hasPermissionDeptIds</td><td>X-Dept-Ids</td></tr>
 *   <tr><td>hasPermissionProjectIds</td><td>X-Project-Ids</td></tr>
 *   <tr><td>hasPermissionRegionIds</td><td>X-Region-Ids</td></tr>
 *   <tr><td>tenantId</td><td>X-Tenant-Id</td></tr>
 *   <tr><td>distinctId</td><td>X-Distinct-Id</td></tr>
 *   <tr><td>requestSource</td><td>X-Request-Source</td></tr>
 *   <tr><td>visibleColumnsByTable</td><td>X-Visible-Columns</td></tr>
 *   <tr><td>editableColumnsByTable</td><td>X-Editable-Columns</td></tr>
 * </table>
 *
 * @see {@link WebAuthInfo}
 * @see {@link AppAuthInfo}
 * @see RequestHolder
 * @see AuthInfoUtils
  *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
public abstract class YdszAuthInfo implements AuthInfo {

    /**
     * 用户系统语言。
     *
     * <p>格式示例：{@code zh-CN}、{@code en-US}。
     * 用于前端国际化展示与后端返回数据格式适配。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String userLanguage;

    /**
     * 用户唯一标识。
     *
     * <p>对应平台用户体系中的主键 ID，非 Token。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String uniqueId;

    /**
     * 用户鉴权 Token。
     *
     * <p>每次登录后由认证服务签发，用于下游服务实时验证。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String accessToken;

    /**
     * 数据权限范围类型。
     *
     * <p>用于标识当前请求的数据权限粒度（如：全部、本人、本部门等）。
     *
     * @see DataScopeType
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private DataScopeType dataScope;

    /**
     * 有权限访问的公司 ID 集合。
     *
     * <p>多值时以 CSV 格式存储（{@code id1,id2,id3}）。
     * 用于 SQL 拦截器自动改写 WHERE 条件。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionCompanyIds;

    /**
     * 有权限访问的部门 ID 集合。
     *
     * <p>多值时以 CSV 格式存储。
     * 与 companyIds 共同构成组织维度权限过滤条件。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionDeptIds;

    /**
     * 有权限访问的项目 ID 集合。
     *
     * <p>多值时以 CSV 格式存储。
     * 项目级数据隔离场景使用。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionProjectIds;

    /**
     * 有权限访问的区域 ID 集合。
     *
     * <p>多值时以 CSV 格式存储。
     * 区域级数据隔离场景使用。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionRegionIds;

    /**
     * 租户唯一标识。
     *
     * <p>用于多租户场景下的数据隔离。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String tenantId;

    /**
     * 设备唯一标识。
     *
     * <p>用于设备追踪、埋点分析等场景。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String distinctId;

    /**
     * 请求来源标识。
     *
     * <p>记录发起请求的来源系统或模块。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String requestSource;

    /**
     * 表级列可见规则。
     *
     * <p>格式：{@code tableName:col1,col2;tableName2:col3}
     * <ul>
     *   <li>key：表名（不区分大小写，统一转小写存储）</li>
     *   <li>value：允许查看的列名集合（不区分大小写）</li>
     * </ul>
     *
     * @see <a href="https://confluence.njydsz.pmis.com.cn/pages/viewpage.action?pageId=123456">列权限设计文档</a>
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Map<String, Set<String>> visibleColumnsByTable = Collections.emptyMap();

    /**
     * 表级列可编辑规则。
     *
     * <p>格式同 {@link #visibleColumnsByTable}。
     * 仅控制列是否可编辑，与可见性独立。
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Map<String, Set<String>> editableColumnsByTable = Collections.emptyMap();

    /**
     * 返回身份类型为公司用户。
     *
     * @return {@link IdentityType#COMPANY}
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public IdentityType getIdentityTypeEnum() {
        return IdentityType.COMPANY;
    }

    /**
     * 返回服务类型码，由子类实现。
     *
     * <p>用于区分请求来源终端：
     * <ul>
     *   <li>{@link ServiceType#WEB_SERVICE} → PC Web</li>
     *   <li>{@link ServiceType#APP_SERVICE} → 移动端 H5/App</li>
     * </ul>
     *
     * @return 服务类型码，非空字符串
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public abstract String getServiceTypeCode();

    /**
     * 获取表级列可见规则。
     *
     * @return 表名→列集合的映射，若无规则返回空 Map
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public Map<String, Set<String>> getVisibleColumnsByTable() {
        return visibleColumnsByTable;
    }

    /**
     * 获取表级列可编辑规则。
     *
     * @return 表名→列集合的映射，若无规则返回空 Map
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public Map<String, Set<String>> getEditableColumnsByTable() {
        return editableColumnsByTable;
    }
}
