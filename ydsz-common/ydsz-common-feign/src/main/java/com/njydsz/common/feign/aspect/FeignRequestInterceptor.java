package com.njydsz.common.feign.aspect;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.trace.TraceIdPropagation;
import com.njydsz.common.core.constant.DataScopeConstants;
import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.util.auth.AuthInfoUtils;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * YdszFeign 远程调用请求拦截器——数据权限全链路透传。
 *
 * <p>在发起 Feign 远程调用前，将当前请求上下文中积累的数据权限信息
 *（行级维度ID + 列级权限规则）透传到下游服务请求头中，保证权限上下文在微服务链路中不丢失。
 *
 * <p><b>透传信息来源（优先级从高到低）：</b>
 * <ol>
 *   <li>{@link AuthInfoUtils}（从 ThreadLocal RequestContext 获取）</li>
 *   <li>当前 HttpServletRequest Header（直接透传）</li>
 *   <li>RequestContext extra headers（{@code @RbacDataScope} AOP 写入的虚拟请求头）</li>
 * </ol>
 *
 * <p><b>透传内容分两类：</b>
 * <ul>
 *   <li><b>行级权限</b>：X-Data-Scope / X-Company-Ids / X-Dept-Ids / X-Project-Ids / X-Region-Ids / X-Tenant-Id / X-Unique-Id</li>
 *   <li><b>列级权限</b>：X-Visible-Columns / X-Editable-Columns（Map 序列化为 {@code table:col1,col2;table2:col3} 格式）</li>
 * </ul>
 *
 * <p><b>写入策略：</b>仅当请求头不存在时才写入（{@code setHeaderIfAbsent}），避免覆盖上游已明确设置的权限上下文。
 *
 * <p><b>配置项：</b>通过 {@link FeignProperties.Propagation} 配置透传的 header 白名单。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FeignProperties
 * @see AuthInfoUtils
 */
public class FeignRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignRequestInterceptor.class);

    /** Feign 配置属性 */
    private final FeignProperties feignProperties;

    /**
     * 使用自定义配置构造请求拦截器。
     *
     * @param feignProperties Feign 配置属性
     */
    public FeignRequestInterceptor(FeignProperties feignProperties) {
        this.feignProperties = feignProperties;
    }

    /**
     * 在发起 Feign 请求前执行：透传数据权限相关请求头。
     *
     * <p><b>执行顺序：</b>
     * <ol>
     *   <li>检查 feign 透传功能是否启用</li>
     *   <li>获取当前 HttpServletRequest（优先从 ServletUtils，其次从 RequestContext）</li>
     *   <li>按配置的白名单透传基础身份类 header（X-Service-Type / X-User-Language / ...）</li>
     *   <li>透传行级权限相关 header（{@link #propagateDataPermissionHeaders}）</li>
     *   <li>透传列级权限相关 header（{@link #propagateColumnPermissionHeaders}）</li>
     * </ol>
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (feignProperties == null
                || feignProperties.getPropagation() == null
                || !feignProperties.getPropagation().isEnabled()
                || feignProperties.getPropagation().getHeaders() == null
                || feignProperties.getPropagation().getHeaders().isEmpty()) {
            return;
        }

        HttpServletRequest httpServletRequest = RequestContextUtils.getRequest();
        if (httpServletRequest == null) {
            log.debug("非Web环境，无法获取Servlet请求上下文");
            httpServletRequest = (HttpServletRequest) RequestContext.get(BizContextKeys.KEY_HTTP_REQUEST);
        }

        Set<String> headersToPropagate = feignProperties.getPropagation().getHeaders();

        // 非Web环境自动生成请求 ID，保证可追溯性
        ensureRequestId(requestTemplate, httpServletRequest);

        // 传播 W3C Trace Context 标准请求头（X-Trace-Id + traceparent），
        // 实现跨服务链路追踪的标准化（兼容 SkyWalking / Jaeger 等）
        propagateTraceHeaders(requestTemplate);

        propagateIdentityHeaders(requestTemplate, httpServletRequest, headersToPropagate);
        propagateDataPermissionHeaders(requestTemplate, headersToPropagate);
        propagateColumnPermissionHeaders(requestTemplate, headersToPropagate);
        propagateNetworkHeaders(requestTemplate, httpServletRequest, headersToPropagate);
    }

    /**
     * 传播 W3C Trace Context 标准请求头。
     *
     * <p>通过 {@link TraceIdPropagation#traceHeadersOrCreate()} 注入：
     * <ul>
     *   <li>{@code X-Trace-Id} — 32位十六进制，与现有体系兼容</li>
     *   <li>{@code traceparent} — W3C Trace Context 标准，格式 {@code 00-{traceId}-{spanId}-01}</li>
     * </ul>
     * 仅当请求头不存在时才写入（setHeaderIfAbsent），避免覆盖上游已明确设置的追踪上下文。
     *
     * @param requestTemplate Feign 请求模板
     * @since 1.2.0
     */
    private void propagateTraceHeaders(RequestTemplate requestTemplate) {
        try {
            java.util.Map<String, String> traceHeaders = TraceIdPropagation.traceHeadersOrCreate();
            if (traceHeaders != null && !traceHeaders.isEmpty()) {
                traceHeaders.forEach((key, value) ->
                    setHeaderIfAbsent(requestTemplate, key, value)
                );
            }
        } catch (Exception e) {
            // 链路追踪传播不应阻断业务请求，静默降级
            log.debug("Trace header propagation failed: {}", e.getMessage());
        }
    }

    /**
     * 透传身份认证相关请求头。
     *
     * <p>包括：服务类型、用户语言、设备标识、身份类型、认证令牌等。
     *
     * @param requestTemplate     Feign 请求模板
     * @param httpServletRequest  HTTP 请求对象
     * @param headersToPropagate  允许透传的 header 白名单
     */
    private void propagateIdentityHeaders(RequestTemplate requestTemplate,
                                           HttpServletRequest httpServletRequest,
                                           Set<String> headersToPropagate) {
        if (headersToPropagate.contains(FeignProperties.X_SERVICE_TYPE)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_SERVICE_TYPE, AuthInfoUtils.getServiceTypeCode());
        }
        if (headersToPropagate.contains(FeignProperties.X_USER_LANGUAGE)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_USER_LANGUAGE, AuthInfoUtils.getUserLanguage());
        }
        if (headersToPropagate.contains(FeignProperties.X_DISTINCT_ID)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_DISTINCT_ID,
                    resolveHeader(httpServletRequest, FeignProperties.X_DISTINCT_ID));
        }
        if (headersToPropagate.contains(FeignProperties.X_IDENTITY_TYPE)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_IDENTITY_TYPE,
                    AuthInfoUtils.getIdentityType());
        }
        if (headersToPropagate.contains(FeignProperties.X_ACCESS_TOKEN)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_ACCESS_TOKEN, AuthInfoUtils.getAccessToken());
        }
        if (headersToPropagate.contains(HeaderConstants.X_REQUEST_SOURCE)) {
            setHeaderIfAbsent(requestTemplate, HeaderConstants.X_REQUEST_SOURCE,
                    resolveHeader(httpServletRequest, HeaderConstants.X_REQUEST_SOURCE));
        }
    }

    /**
     * 透传网络相关请求头。
     *
     * <p>包括：X-Forwarded-For（客户端真实 IP）等。
     *
     * @param requestTemplate     Feign 请求模板
     * @param httpServletRequest  HTTP 请求对象
     * @param headersToPropagate  允许透传的 header 白名单
     */
    private void propagateNetworkHeaders(RequestTemplate requestTemplate,
                                          HttpServletRequest httpServletRequest,
                                          Set<String> headersToPropagate) {
        if (headersToPropagate.contains(HeaderConstants.X_FORWARDED_FOR)
                && !hasHeader(requestTemplate, HeaderConstants.X_FORWARDED_FOR)) {
            String forwardedFor = resolveHeader(httpServletRequest, HeaderConstants.X_FORWARDED_FOR);
            if (StringUtils.isEmpty(forwardedFor) && httpServletRequest != null) {
                forwardedFor = ClientIpResolver.getClientIp(httpServletRequest);
            }
            setHeaderIfAbsent(requestTemplate, HeaderConstants.X_FORWARDED_FOR, forwardedFor);
        }
    }

    /**
     * 透传行级数据权限相关请求头。
     *
     * <p>根据当前用户的 dataScope 类型决定透传哪些维度ID：
     * <ul>
     *   <li>group：透传 X-Company-Ids（逗号分隔）</li>
     *   <li>company / dept：透传 X-Dept-Ids（逗号分隔）</li>
     *   <li>project：透传 X-Project-Ids（逗号分隔）</li>
     *   <li>region：透传 X-Region-Ids（逗号分隔）</li>
     *   <li>任意 scope：始终透传 X-Data-Scope / X-Unique-Id / X-Tenant-Id</li>
     * </ul>
     *
     * @param requestTemplate    Feign 请求模板
     * @param headersToPropagate 允许透传的 header 白名单
     */
    private void propagateDataPermissionHeaders(RequestTemplate requestTemplate, Set<String> headersToPropagate) {
        if (headersToPropagate.contains(FeignProperties.X_DATA_SCOPE)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_DATA_SCOPE,
                    AuthInfoUtils.getDataScope());
        }

        String scopeCode = AuthInfoUtils.getDataScope();
        if (headersToPropagate.contains(FeignProperties.X_COMPANY_IDS)) {
            if (DataScopeConstants.GROUP.equals(scopeCode)) {
                Set<String> companyIdsSet = AuthInfoUtils.getHasPermissionCompanyIds();
                if (companyIdsSet != null && !companyIdsSet.isEmpty()) {
                    setHeaderIfAbsent(requestTemplate, FeignProperties.X_COMPANY_IDS, String.join(",", companyIdsSet));
                }
            }
        }
        if (headersToPropagate.contains(FeignProperties.X_DEPT_IDS)) {
            if (DataScopeConstants.COMPANY.equals(scopeCode) || DataScopeConstants.DEPT.equals(scopeCode)) {
                Set<String> deptIdsSet = AuthInfoUtils.getHasPermissionDeptIds();
                if (deptIdsSet != null && !deptIdsSet.isEmpty()) {
                    setHeaderIfAbsent(requestTemplate, FeignProperties.X_DEPT_IDS, String.join(",", deptIdsSet));
                }
            }
        }
        if (headersToPropagate.contains(FeignProperties.X_UNIQUE_ID)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_UNIQUE_ID, AuthInfoUtils.getUniqueId());
        }
        if (headersToPropagate.contains(FeignProperties.X_TENANT_ID)) {
            setHeaderIfAbsent(requestTemplate, FeignProperties.X_TENANT_ID,
                    resolveHeader(RequestContextUtils.getRequest(), FeignProperties.X_TENANT_ID));
        }
        if (headersToPropagate.contains(FeignProperties.X_PROJECT_IDS)) {
            if (DataScopeConstants.PROJECT.equals(scopeCode)) {
                Set<String> projectIdsSet = AuthInfoUtils.getHasPermissionProjectIds();
                if (projectIdsSet != null && !projectIdsSet.isEmpty()) {
                    setHeaderIfAbsent(requestTemplate, FeignProperties.X_PROJECT_IDS, String.join(",", projectIdsSet));
                }
            }
        }
        if (headersToPropagate.contains(FeignProperties.X_REGION_IDS)) {
            if (DataScopeConstants.REGION.equals(scopeCode)) {
                Set<String> regionIdsSet = AuthInfoUtils.getHasPermissionRegionIds();
                if (regionIdsSet != null && !regionIdsSet.isEmpty()) {
                    setHeaderIfAbsent(requestTemplate, FeignProperties.X_REGION_IDS, String.join(",", regionIdsSet));
                }
            }
        }
    }

    /**
     * 透传列级数据权限相关请求头。
     *
     * <p>将 AuthInfo 中的列级权限 Map 序列化为标准格式后透传：
     * <ul>
     *   <li>X-Visible-Columns：格式 {@code table:col1,col2;table2:col3}</li>
     *   <li>X-Editable-Columns：格式同上</li>
     * </ul>
     *
     * <p>只有当 AuthInfo 中存在非空规则时才写入，避免下游服务误解为"全禁"或"全开"。
     *
     * @param requestTemplate    Feign 请求模板
     * @param headersToPropagate 允许透传的 header 白名单
     */
    private void propagateColumnPermissionHeaders(RequestTemplate requestTemplate, Set<String> headersToPropagate) {
        if (headersToPropagate.contains(FeignProperties.X_VISIBLE_COLUMNS)) {
            Map<String, Set<String>> visible = AuthInfoUtils.getVisibleColumnsByTable();
            if (visible != null && !visible.isEmpty()) {
                String formatted = formatTableColumnsRule(visible);
                setHeaderIfAbsent(requestTemplate, FeignProperties.X_VISIBLE_COLUMNS, formatted);
            }
        }
        if (headersToPropagate.contains(FeignProperties.X_EDITABLE_COLUMNS)) {
            Map<String, Set<String>> editable = AuthInfoUtils.getEditableColumnsByTable();
            if (editable != null && !editable.isEmpty()) {
                String formatted = formatTableColumnsRule(editable);
                setHeaderIfAbsent(requestTemplate, FeignProperties.X_EDITABLE_COLUMNS, formatted);
            }
        }
    }

    /**
     * 将表级列规则 Map 序列化为标准格式字符串。
     *
     * <p>格式：{@code table1:col1,col2;table2:col3,col4}
     * <ul>
     *   <li>分号 {@code ;} 分隔不同表</li>
     *   <li>冒号 {@code :} 分隔表名和列名</li>
     *   <li>逗号 {@code ,} 分隔同表多列</li>
     *   <li>表名和列名均小写化</li>
     * </ul>
     *
     * @param tableColumns 表名到列集合的映射
     * @return 序列化后的字符串；若为空返回 null
     */
    private String formatTableColumnsRule(Map<String, Set<String>> tableColumns) {
        if (tableColumns == null || tableColumns.isEmpty()) {
            return null;
        }
        return tableColumns.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey().toLowerCase() + ":" + e.getValue().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    /**
     * 确保请求 ID 存在。非Web环境下自动生成 traceId 作为请求标识。
     *
     * @param requestTemplate Feign 请求模板
     * @param request         HttpServletRequest（可为 null）
     */
    private void ensureRequestId(RequestTemplate requestTemplate, HttpServletRequest request) {
        if (hasHeader(requestTemplate, HeaderConstants.X_REQUEST_ID)) {
            return;
        }
        String requestId = resolveHeader(request, HeaderConstants.X_REQUEST_ID);
        if (StringUtils.isEmpty(requestId)) {
            requestId = TracerUtils.getTraceId();
            if (StringUtils.isEmpty(requestId)) {
                requestId = TracerUtils.generateTraceId();
            }
        }
        setHeaderIfAbsent(requestTemplate, HeaderConstants.X_REQUEST_ID, requestId);
    }

    /**
     * 读取请求头，优先从 HttpServletRequest，兜底从 RequestContext extra headers。
     *
     * @param request    HttpServletRequest（可为 null）
     * @param headerName header 名称
     * @return header 值；均不存在返回 null
     */
    private String resolveHeader(HttpServletRequest request, String headerName) {
        if (request != null) {
            String value = request.getHeader(headerName);
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return RequestContext.getExtraHeader(headerName);
    }

    /**
     * 仅当 header 不存在时写入。
     *
     * @param requestTemplate Feign 请求模板
     * @param headerName      header 名称
     * @param headerValue     header 值（null 或空字符串不写入）
     */
    private void setHeaderIfAbsent(RequestTemplate requestTemplate, String headerName, String headerValue) {
        if (StringUtils.isNotEmpty(headerValue) && !hasHeader(requestTemplate, headerName)) {
            requestTemplate.header(headerName, headerValue);
        }
    }

    /**
     * 判断 Feign 请求模板中是否已存在指定 header。
     *
     * @param requestTemplate Feign 请求模板
     * @param headerName      header 名称
     * @return true=已存在，false=不存在
     */
    private boolean hasHeader(RequestTemplate requestTemplate, String headerName) {
        return requestTemplate.headers() != null
                && requestTemplate.headers().get(headerName) != null
                && !requestTemplate.headers().get(headerName).isEmpty();
    }
}
