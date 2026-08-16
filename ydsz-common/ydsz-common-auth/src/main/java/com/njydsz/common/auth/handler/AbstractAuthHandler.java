package com.njydsz.common.auth.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.auth.util.PermissionUtils;
import com.njydsz.common.auth.model.AuthInfo;
import com.njydsz.common.auth.model.YdszAuthInfo;
import com.njydsz.common.util.string.StringUtils;

/**
 * 认证信息处理抽象基类
 *
 * <p>提供通用的请求头解析逻辑，子类只需实现 {@link #createAuthInfo()} 返回具体的 AuthInfo 实现类。
 * 采用模板方法模式，统一的解析流程由本类管理，差异化实例创建由子类提供。
 *
 * <p>自 v2.0.0 起从 util 层迁移至 common-auth 服务层，
 * 已移除对旧版弃用类的继承依赖，新代码应继承本类。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see AuthHandler
 */
public abstract class AbstractAuthHandler implements AuthHandler {

    /**
     * 创建认证信息实例（模板方法模式）
     *
     * <p>子类实现此方法返回具体的 AuthInfo 实现类实例，
     * 如 WebAuthInfo 或 AppAuthInfo。
     *
     * @return 空的 AuthInfo 实例
     */
    protected abstract YdszAuthInfo createAuthInfo();

    /**
     * 解析请求头并构建认证信息（模板方法）
     *
     * <p>统一解析逻辑，子类仅需提供不同的 AuthInfo 实例类型。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 填充完毕的认证信息
     */
    @Override
    public AuthInfo getAuthInfo(HttpServletRequest request, HttpServletResponse response) {
        ParsedAuthHeaders h = ParsedAuthHeaders.parse(request, this);

        AuthInfo info = createAuthInfo();
        if (info instanceof YdszAuthInfo YdszAuthInfo) {
            YdszAuthInfo.setUserLanguage(h.getLanguage());
            YdszAuthInfo.setDistinctId(h.getDistinctId());
            YdszAuthInfo.setAccessToken(h.getAuthToken());
            YdszAuthInfo.setDataScope(h.getDataScope());
            YdszAuthInfo.setHasPermissionCompanyIds(h.getCompanyIds());
            YdszAuthInfo.setHasPermissionDeptIds(h.getDeptIds());
            YdszAuthInfo.setUniqueId(h.getUserId());
            YdszAuthInfo.setTenantId(h.getTenantId());
            YdszAuthInfo.setHasPermissionProjectIds(h.getProjectIds());
            YdszAuthInfo.setHasPermissionRegionIds(h.getRegionIds());
            YdszAuthInfo.setRequestSource(h.getRequestSource());
            YdszAuthInfo.setVisibleColumnsByTable(h.getVisibleColumns());
            YdszAuthInfo.setEditableColumnsByTable(h.getEditableColumns());
        }

        return info;
    }

    /**
     * 解析 CSV 格式的请求头值为 Set
     *
     * @param request HTTP 请求对象
     * @param headerName 请求头名称
     * @return 解析后的值集合，解析失败返回空 Set
     */
    protected Set<String> parseCsvHeaderValues(HttpServletRequest request, String headerName) {
        if (request == null || StringUtils.isBlank(headerName)) {
            return Collections.emptySet();
        }
        Enumeration<String> headers = request.getHeaders(headerName);
        if (headers == null) {
            return Collections.emptySet();
        }
        return Collections.list(headers).stream()
                .flatMap(this::splitCsv)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 按逗号分割字符串并过滤空值
     *
     * <p>委托给 {@link com.njydsz.common.auth.util.PermissionUtils#splitCsv} 统一处理 CSV 解析，
     * 消除重复逻辑。
     *
     * @param value 待分割的字符串
     * @return 分割后的字符串流，空值时返回空流
     */
    protected Stream<String> splitCsv(String value) {
        if (StringUtils.isBlank(value)) {
            return Stream.empty();
        }
        return PermissionUtils.splitCsv(value).stream();
    }

    /**
     * 解析表级列权限规则字符串
     *
     * <p>格式：{@code table:col1,col2;table2:col3}，表名和列名统一转小写
     *
     * @param value 列权限规则字符串
     * @return 表名到列名集合的映射，解析失败返回空 Map
     */
    protected Map<String, Set<String>> parseTableColumnsRule(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        String[] blocks = value.split(";");
        for (String block : blocks) {
            if (StringUtils.isBlank(block) || !block.contains(":")) {
                continue;
            }
            String[] pair = block.split(":", 2);
            String table = pair[0].trim().toLowerCase(Locale.ROOT);
            Set<String> cols = splitCsv(pair[1])
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (StringUtils.isNotBlank(table) && !cols.isEmpty()) {
                out.computeIfAbsent(table, key -> new LinkedHashSet<>()).addAll(cols);
            }
        }
        return out;
    }
}
