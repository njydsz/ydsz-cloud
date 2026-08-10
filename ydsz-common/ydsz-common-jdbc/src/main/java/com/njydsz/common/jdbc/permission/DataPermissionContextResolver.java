package com.njydsz.common.jdbc.permission;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.constant.DataScopeConstants;
import com.njydsz.common.util.auth.AuthInfoUtils;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * 数据权限上下文解析器——SQL 拦截器的上游。
 *
 * <p>从请求上下文中读取数据权限相关的 HTTP 请求头，构建 {@link DataPermissionContext}，
 * 供 {@link DataPermissionInnerInterceptor} 在 SQL 执行前改写 WHERE/JOIN 条件。
 *
 * <h2>读取的 Header 清单（与 {@link DataPermissionHeaderConstants} 编号对齐）</h2>
 * <ul>
 *   <li>X-Data-Scope (6) - 数据权限范围类型，决定按哪个维度过滤</li>
 *   <li>X-Company-Ids (7) - 公司ID集合（GROUP 范围）</li>
 *   <li>X-Dept-Ids (8) - 部门ID集合（COMPANY/DEPT 范围）</li>
 *   <li>X-Unique-Id (9) - 用户ID（USER 范围）</li>
 *   <li>X-Project-Ids (11) - 项目ID集合（PROJECT 范围）</li>
 *   <li>X-Region-Ids (12) - 区域ID集合（REGION 范围）</li>
 *   <li>X-Visible-Columns (14) - 列可见规则（SELECT 过滤）</li>
 *   <li>X-Editable-Columns (15) - 列可编辑规则（INSERT/UPDATE 过滤）</li>
 * </ul>
 *
 * <p><b>注意：</b>租户ID（X-Tenant-Id）已由独立的 {@code common-tenant} 模块
 * 通过 {@code TenantContextWebFilter} + {@code TenantIsolationInterceptor} 处理，
 * 本解析器不再负责租户上下文。
 *
 * <h2>读取优先级（安全增强）</h2>
 * <ol>
 *   <li>认证上下文 {@link AuthInfoUtils}（JWT 解析，可信）— 用于 userId</li>
 *   <li>真实 HttpServletRequest Header（常规 Web 请求 / Feign 透传）— 用于 ID 集合、列权限</li>
 *   <li>{@link RequestContext} extra headers（{@code @AuthRowPermission}/{@code @AuthColPermission} 写入的虚拟请求头）</li>
 * </ol>
 *
 * <p><b>安全说明：</b> userId 优先从 JWT 认证上下文获取（不可伪造），
 * HTTP Header 中的值仅在认证上下文不可用时作为兼容回退。生产环境应确保 API 网关
 * 清洗外部请求中的 X-Unique-Id 等敏感 Header，仅允许网关写入。
 *
 * <h2>ID 扩展</h2>
 * <p>支持可选的 {@link DataScopeIdExpander}，在已知 ID 集合基础上自动扩展下级子节点：
 * <ul>
 *   <li>group scope：扩展 companyIds -> 下级公司</li>
 *   <li>company/dept scope：扩展 deptIds -> 下级部门</li>
 *   <li>project scope：扩展 projectIds -> 下级项目</li>
 *   <li>region scope：扩展 regionIds -> 下级区域</li>
 * </ul>
 *
 * @see DataPermissionContext
 * @see DataPermissionInnerInterceptor
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DataPermissionContextResolver {

    private final DataScopeIdExpander idExpander;

    public DataPermissionContextResolver() {
        this(null);
    }

    public DataPermissionContextResolver(DataScopeIdExpander idExpander) {
        this.idExpander = idExpander;
    }

    /**
     * 解析当前调用链的数据权限上下文。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>获取当前请求（优先从 ServletUtils，兜底从 RequestContext）</li>
     *   <li>读取并解析所有数据权限相关 header</li>
     *   <li>调用 {@link #expandIdsIfNecessary(DataPermissionContext)} 扩展子级 ID</li>
     *   <li>返回完整的 DataPermissionContext</li>
     * </ol>
     *
     * @return 数据权限上下文；所有字段均不为 null（集合为空 Set/Map）
     */
    public DataPermissionContext resolve() {
        HttpServletRequest request = RequestContextUtils.getRequest();
        if (request == null) {
            request = (HttpServletRequest) RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
        }
        DataPermissionContext context = new DataPermissionContext();
        context.setDataScope(resolveDataScope(resolveHeader(request, DataPermissionHeaderConstants.X_DATA_SCOPE)));
        // 安全增强：userId 优先从认证上下文（JWT）获取，不可伪造
        String authUserId = AuthInfoUtils.getUniqueId();
        context.setUserId(trimToNull(authUserId != null ? authUserId : resolveHeader(request, DataPermissionHeaderConstants.X_UNIQUE_ID)));
        context.setCompanyIds(splitCsv(resolveHeader(request, DataPermissionHeaderConstants.X_COMPANY_IDS)));
        context.setDeptIds(splitCsv(resolveHeader(request, DataPermissionHeaderConstants.X_DEPT_IDS)));
        context.setProjectIds(splitCsv(resolveHeader(request, DataPermissionHeaderConstants.X_PROJECT_IDS)));
        context.setRegionIds(splitCsv(resolveHeader(request, DataPermissionHeaderConstants.X_REGION_IDS)));
        context.setVisibleColumnsByTable(parseTableColumnsRule(resolveHeader(request, DataPermissionHeaderConstants.X_VISIBLE_COLUMNS)));
        context.setEditableColumnsByTable(parseTableColumnsRule(resolveHeader(request, DataPermissionHeaderConstants.X_EDITABLE_COLUMNS)));
        expandIdsIfNecessary(context);
        return context;
    }

    /**
     * 根据 scope 类型编码扩展维度ID集合（获取下级节点）。
     *
     * <p>扩展策略：
     * <ul>
     *   <li>group：扩展 companyIds</li>
     *   <li>company / dept：扩展 deptIds</li>
     *   <li>project：扩展 projectIds</li>
     *   <li>region：扩展 regionIds</li>
     * <li>其他（user）或 idExpander 为 null：不做扩展</li>
     * </ul>
     *
     * @param context 数据权限上下文（会被直接修改）
     */
    private void expandIdsIfNecessary(DataPermissionContext context) {
        if (context == null || idExpander == null) {
            return;
        }
        String scope = context.getDataScope();
        if (scope == null) {
            return;
        }
        if (DataScopeConstants.GROUP.equals(scope)) {
            context.setCompanyIds(idExpander.expandCompanyIds(context.getCompanyIds()));
            return;
        }
        if (DataScopeConstants.COMPANY.equals(scope) || DataScopeConstants.DEPT.equals(scope)) {
            context.setDeptIds(idExpander.expandDeptIds(context.getDeptIds()));
            return;
        }
        if (DataScopeConstants.PROJECT.equals(scope)) {
            context.setProjectIds(idExpander.expandProjectIds(context.getProjectIds()));
            return;
        }
        if (DataScopeConstants.REGION.equals(scope)) {
            context.setRegionIds(idExpander.expandRegionIds(context.getRegionIds()));
            return;
        }
    }

    /**
     * 解析 dataScope 字符串为编码字符串。
     *
     * @param dataScopeValue scope 码值（如 "company"、"dept"）
     * @return 编码字符串；为空返回 null
     */
    private String resolveDataScope(String dataScopeValue) {
        String code = trimToNull(dataScopeValue);
        if (code == null) {
            return null;
        }
        return code;
    }

    /**
     * 读取请求头，优先从 HttpServletRequest，兜底从 RequestContext extra headers。
     *
     * @param request HttpServletRequest（可为 null）
     * @param name    header 名称
     * @return header 值；均不存在返回 null
     */
    private String resolveHeader(HttpServletRequest request, String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        if (request != null) {
            String value = request.getHeader(name);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return RequestContext.getExtraHeader(name);
    }

    /**
     * 安全转换为 null：空字符串返回 null。
     *
     * @param value 原始字符串
     * @return 修剪后字符串或 null
     */
    private String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 将逗号分隔字符串解析为 LinkedHashSet（保持顺序 + 去重）。
     *
     * @param value 逗号分隔字符串
     * @return 去重后的 ID 集合；空返回空 Set
     */
    private Set<String> splitCsv(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> StringUtils.isNotBlank(s))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 解析列级权限规则字符串。
     *
     * <p>格式：{@code table1:col1,col2;table2:col3,col4}
     *
     * <p>解析规则：
     * <ul>
     *   <li>分号分割不同表</li>
     *   <li>冒号分割表名和列名</li>
     *   <li>逗号分割同表多列</li>
     *   <li>表名和列名均小写化</li>
     * </ul>
     *
     * @param value 规则字符串
     * @return 表名到列集合的映射；空或格式异常返回空 Map
     */
    private Map<String, Set<String>> parseTableColumnsRule(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> out = new HashMap<>();
        String[] blocks = value.split(";");
        for (String block : blocks) {
            if (StringUtils.isBlank(block) || !block.contains(":")) {
                continue;
            }
            String[] pair = block.split(":", 2);
            String table = pair[0].trim().toLowerCase();
            Set<String> cols = splitCsv(pair[1]).stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (StringUtils.isNotBlank(table) && !cols.isEmpty()) {
                out.put(table, cols);
            }
        }
        return out;
    }
}
