package com.njydsz.common.auth.handler;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.enums.DataScopeType;
import com.njydsz.common.util.string.StringUtils;

/**
 * 公共请求头解析结果
 *
 * <p>封装从 HTTP 请求头中解析出的所有认证相关字段，
 * 供 {@code WebAuthHandler} / {@code AppAuthHandler} 直接使用，
 * 避免在两个 handler 中重复编写相同的 13 行 header.getHeader() 代码。
 *
 * @since 1.0.0
 * 
 */
public class ParsedAuthHeaders {

    private static final Logger log = LoggerFactory.getLogger(ParsedAuthHeaders.class);

    private final String language;
    private final String distinctId;
    private final String authToken;
    private final DataScopeType dataScope;
    private final Set<String> companyIds;
    private final Set<String> deptIds;
    private final String userId;
    private final String tenantId;
    private final Set<String> projectIds;
    private final Set<String> regionIds;
    private final String requestSource;
    private final Map<String, Set<String>> visibleColumns;
    private final Map<String, Set<String>> editableColumns;

    private ParsedAuthHeaders(Builder builder) {
        this.language = builder.language;
        this.distinctId = builder.distinctId;
        this.authToken = builder.authToken;
        this.dataScope = builder.dataScope;
        this.companyIds = builder.companyIds;
        this.deptIds = builder.deptIds;
        this.userId = builder.userId;
        this.tenantId = builder.tenantId;
        this.projectIds = builder.projectIds;
        this.regionIds = builder.regionIds;
        this.requestSource = builder.requestSource;
        this.visibleColumns = builder.visibleColumns;
        this.editableColumns = builder.editableColumns;
    }

    public static ParsedAuthHeaders parse(HttpServletRequest request, AbstractAuthHandler handler) {
        Builder b = new Builder();
        b.language = request.getHeader(HeaderConstants.X_USER_LANGUAGE);
        b.distinctId = request.getHeader(HeaderConstants.X_DISTINCT_ID);
        b.authToken = request.getHeader(HeaderConstants.X_ACCESS_TOKEN);
        b.companyIds = handler.parseCsvHeaderValues(request, HeaderConstants.X_COMPANY_IDS);
        b.deptIds = handler.parseCsvHeaderValues(request, HeaderConstants.X_DEPT_IDS);
        b.userId = request.getHeader(HeaderConstants.X_UNIQUE_ID);
        b.tenantId = request.getHeader(HeaderConstants.X_TENANT_ID);
        b.projectIds = handler.parseCsvHeaderValues(request, HeaderConstants.X_PROJECT_IDS);
        b.regionIds = handler.parseCsvHeaderValues(request, HeaderConstants.X_REGION_IDS);
        b.requestSource = request.getHeader(HeaderConstants.X_REQUEST_SOURCE);
        b.visibleColumns = handler.parseTableColumnsRule(request.getHeader(HeaderConstants.X_VISIBLE_COLUMNS));
        b.editableColumns = handler.parseTableColumnsRule(request.getHeader(HeaderConstants.X_EDITABLE_COLUMNS));

        String dataScopeCode = request.getHeader(HeaderConstants.X_DATA_SCOPE);
        b.dataScope = parseDataScope(dataScopeCode);
        return new ParsedAuthHeaders(b);
    }

    private static DataScopeType parseDataScope(String dataScopeCode) {
        if (StringUtils.isBlank(dataScopeCode)) {
            return null;
        }
        try {
            return DataScopeType.codeOf(dataScopeCode);
        } catch (RuntimeException e) {
            log.warn("数据权限范围类型解析失败，code={}", dataScopeCode);
            return null;
        }
    }

    // --- getters ---

    public String getLanguage() { return language; }
    public String getDistinctId() { return distinctId; }
    public String getAuthToken() { return authToken; }
    public DataScopeType getDataScope() { return dataScope; }
    public Set<String> getCompanyIds() { return companyIds; }
    public Set<String> getDeptIds() { return deptIds; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public Set<String> getProjectIds() { return projectIds; }
    public Set<String> getRegionIds() { return regionIds; }
    public String getRequestSource() { return requestSource; }
    public Map<String, Set<String>> getVisibleColumns() { return visibleColumns; }
    public Map<String, Set<String>> getEditableColumns() { return editableColumns; }

    private static class Builder {
        String language, distinctId, authToken, userId, tenantId, requestSource;
        DataScopeType dataScope;
        Set<String> companyIds, deptIds, projectIds, regionIds;
        Map<String, Set<String>> visibleColumns, editableColumns;
    }
}
