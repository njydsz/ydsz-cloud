package com.njydsz.common.auth.handler;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.util.string.StringUtils;

/**
 * 公共请求头解析结果
 *
 * <p>封装从 HTTP 请求头中解析出的所有认证相关字段， 供 {@code WebAuthHandler} / {@code AppAuthHandler} 直接使用， 避免在两个
 * handler 中重复编写相同的 13 行 header.getHeader() 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ParsedAuthHeaders {

  private static final Logger LOG = LoggerFactory.getLogger(ParsedAuthHeaders.class);

  private final String language;
  private final String distinctId;
  private final String authToken;
  private final String dataScope;
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

  /**
   * 从 HTTP 请求头解析出全部认证相关字段。
   *
   * <p>统一读取 {@code X-*} 认证请求头并委托 {@link AbstractAuthHandler} 解析 CSV 集合类字段；
   * 数据范围编码解析失败时返回原始字符串而非抛出异常，容忍非法值。结果对象字段全部不可变。
   *
   * @param request 当前 HTTP 请求，不可为 {@code null}
   * @param handler 认证请求头解析处理器，提供 CSV/规则串解析能力
   * @return 解析完成的认证请求头快照，永不为 {@code null}
   */
  public static ParsedAuthHeaders parse(HttpServletRequest request, AbstractAuthHandler handler) {
    Builder b = new Builder();
    b.language = request.getHeader(AuthHeaderConstants.X_USER_LANGUAGE);
    b.distinctId = request.getHeader(AuthHeaderConstants.X_DISTINCT_ID);
    b.authToken = request.getHeader(AuthHeaderConstants.X_ACCESS_TOKEN);
    b.companyIds =
        handler.parseCsvHeaderValues(request, DataPermissionHeaderConstants.X_COMPANY_IDS);
    b.deptIds = handler.parseCsvHeaderValues(request, DataPermissionHeaderConstants.X_DEPT_IDS);
    b.userId = request.getHeader(DataPermissionHeaderConstants.X_UNIQUE_ID);
    b.tenantId = request.getHeader(DataPermissionHeaderConstants.X_TENANT_ID);
    b.projectIds =
        handler.parseCsvHeaderValues(request, DataPermissionHeaderConstants.X_PROJECT_IDS);
    b.regionIds = handler.parseCsvHeaderValues(request, DataPermissionHeaderConstants.X_REGION_IDS);
    b.requestSource = request.getHeader(HeaderConstants.X_REQUEST_SOURCE);
    b.visibleColumns =
        handler.parseTableColumnsRule(
            request.getHeader(DataPermissionHeaderConstants.X_VISIBLE_COLUMNS));
    b.editableColumns =
        handler.parseTableColumnsRule(
            request.getHeader(DataPermissionHeaderConstants.X_EDITABLE_COLUMNS));

    String dataScopeCode = request.getHeader(DataPermissionHeaderConstants.X_DATA_SCOPE);
    b.dataScope = parseDataScope(dataScopeCode);
    return new ParsedAuthHeaders(b);
  }

  private static String parseDataScope(String dataScopeCode) {
    if (StringUtils.isBlank(dataScopeCode)) {
      return null;
    }
    return dataScopeCode.trim();
  }

  // --- getters ---

  public String getLanguage() {
    return language;
  }

  public String getDistinctId() {
    return distinctId;
  }

  public String getAuthToken() {
    return authToken;
  }

  public String getDataScope() {
    return dataScope;
  }

  public Set<String> getCompanyIds() {
    return companyIds;
  }

  public Set<String> getDeptIds() {
    return deptIds;
  }

  public String getUserId() {
    return userId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Set<String> getProjectIds() {
    return projectIds;
  }

  public Set<String> getRegionIds() {
    return regionIds;
  }

  public String getRequestSource() {
    return requestSource;
  }

  public Map<String, Set<String>> getVisibleColumns() {
    return visibleColumns;
  }

  public Map<String, Set<String>> getEditableColumns() {
    return editableColumns;
  }

  /**
   * 认证请求头解析结果构建器。
   *
   * <p>由 {@link #parse} 内部使用，承载各请求头的原始解析值，构造完成后组装为不可变结果对象。
   */
  private static class Builder {
    String language, distinctId, authToken, userId, tenantId, requestSource;
    String dataScope;
    Set<String> companyIds, deptIds, projectIds, regionIds;
    Map<String, Set<String>> visibleColumns, editableColumns;
  }
}
