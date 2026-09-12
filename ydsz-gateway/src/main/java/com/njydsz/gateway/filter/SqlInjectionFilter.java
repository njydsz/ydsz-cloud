package com.njydsz.gateway.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.SqlInjectionProperties;
import com.njydsz.gateway.exception.GatewayErrorWriter;

/**
 * SQL 注入检测全局过滤器。
 *
 * <p>在网关层对所有请求查询参数（query parameters）的值做轻量级 SQL 注入特征正则检测。
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><b>宁可误报也要拦截</b>：正则覆盖面广，宁可误报也不放过潜在攻击
 *   <li><b>仅检测 query parameters</b>：不消耗 request body，避免性能开销
 *   <li><b>白名单参数名</b>：tenantId、page、size、sort 等分页参数不参与检测
 *   <li><b>空值跳过</b>：参数值为 null 或为空时不检测
 * </ul>
 *
 * <h3>检测规则（预编译 Pattern，YDIZ-CONC-003 合规）</h3>
 *
 * <ul>
 *   <li>经典注入：' OR '1'='1、' OR 1=1、' OR ''='
 *   <li>堆叠查询：; DROP TABLE、; DELETE FROM、; UPDATE ... SET
 *   <li>联合查询：UNION SELECT、UNION ALL SELECT
 *   <li>执行函数：EXEC(、EXECUTE(、sp_executesql
 *   <li>注释符：--、/* ... *&#47;
 *   <li>编码绕过：CHAR()、CONCAT()、0x 十六进制
 * </ul>
 *
 * <h3>配置方式</h3>
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     filter:
 *       sql-injection: true          # 总开关
 *     sql-injection:
 *       enabled: true                 # 过滤器独立开关
 *       mode: STANDARD                # STANDARD / STRICT
 *       auto-block: true              # 命中后是否自动封禁 IP
 *       auto-block-threshold: 3      # 触发封禁的命中次数
 *       auto-block-ttl-seconds: 3600 # 封禁时长
 *       whitelist-param-names:
 *         - tenantId
 *         - page
 *         - size
 *         - sort
 * </pre>
 *
 * <p><b>职责边界：</b>本过滤器仅在网关层做轻量级正则检测，<b>不</b>解析 SQL AST，
 * 深度 SQL 注入防御由下游服务使用预编译 PreparedStatement 负责。
 *
 * <p><b>与 ydsz-common-jdbc 的关系（ADR-4，见 docs/ADR-2026-09-12_公共能力重复实现收敛决策.md）：</b>
 * common-jdbc 的 {@code SqlFirewallInnerInterceptor} 在 JDBC 层做深度防护，本过滤器在网关层做入口拦截，
 * 二者构成<b>纵深防御</b>而非重复建设；网关层为响应式栈，无法直接复用 Servlet 端实现。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "sql-injection",
    havingValue = "true",
    matchIfMissing = true)
public class SqlInjectionFilter implements GlobalFilter, Ordered {

  /** STANDARD 模式规则集：覆盖常见 SQL 注入特征（经典注入、堆叠查询、联合查询、执行函数、注释符、编码绕过）。 */
  private static final List<Pattern> STANDARD_PATTERNS = new ArrayList<>(16);

  /** STRICT 模式规则集：在 STANDARD 基础上增加 {@link #P_CLASSIC_OR_EMPTY} 与 {@link #P_UPDATE_SET} 等额外规则。 */
  private static final List<Pattern> STRICT_PATTERNS = new ArrayList<>(16);

  // ---- 经典注入模式 ----
  /** 经典恒真注入：' OR '1'='1、' OR 1=1、' OR ''=' */
  private static final Pattern P_CLASSIC_OR =
      Pattern.compile("('|\")\\s*[OoRr]\\s*('|\")?\\s*\\d+\\s*=\\s*\\d+\\s*('|\")?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern P_CLASSIC_OR_EMPTY =
      Pattern.compile("('|\")\\s*[OoRr]\\s*('|\")?\\s*('|\")\\s*=\\s*('|\")",
          Pattern.CASE_INSENSITIVE);

  // ---- 堆叠查询模式 ----
  /** 堆叠危险语句：; DROP TABLE、; DELETE FROM、; UPDATE ... SET */
  private static final Pattern P_DROP_TABLE =
      Pattern.compile(";\\s*[Dd][Rr][Oo][Pp]\\s+[Tt][Aa][Bb][Ll][Ee]");
  private static final Pattern P_DELETE_FROM =
      Pattern.compile(";\\s*[Dd][Ee][Ll][Ee][Tt][Ee]\\s+[Ff][Rr][Oo][Mm]");
  private static final Pattern P_UPDATE_SET =
      Pattern.compile(";\\s*[Uu][Pp][Dd][Aa][Tt][Ee]\\s+.*[Ss][Ee][Tt]");

  // ---- 联合查询模式 ----
  /** 联合查询注入：UNION SELECT、UNION ALL SELECT */
  private static final Pattern P_UNION_SELECT =
      Pattern.compile("[Uu][Nn][Ii][Oo][Nn]\\s+[Aa][Ll][Ll]\\s+[Ss][Ee][Ll][Ee][Cc][Tt]");
  private static final Pattern P_UNION_SELECT_SIMPLE =
      Pattern.compile("[Uu][Nn][Ii][Oo][Nn]\\s+[Ss][Ee][Ll][Ee][Cc][Tt]");

  // ---- 执行函数模式 ----
  /** 执行函数：EXEC(、EXECUTE(、sp_executesql */
  private static final Pattern P_EXEC =
      Pattern.compile("[Ee][Xx][Ee][Cc][Uu][Tt][Ee]?\\s*\\(");
  private static final Pattern P_SP_EXEC =
      Pattern.compile("[Ss]_[Pp]_[Ee][Xx][Ee][Cc][Uu][Tt][Ee][Ss][Qq][Ll]");

  // ---- 注释符模式 ----
  /** SQL 行注释：-- */
  private static final Pattern P_LINE_COMMENT = Pattern.compile("--");
  /** SQL 块注释：/* ... *&#47; */
  private static final Pattern P_BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

  // ---- 编码绕过模式 ----
  /** 编码/拼接函数：CHAR()、CONCAT() */
  private static final Pattern P_ENCODING_FUNC =
      Pattern.compile("(CHAR|CONCAT)\\s*\\(", Pattern.CASE_INSENSITIVE);
  /** 十六进制编码：0x 后接十六进制字符（查询参数中出现视为可疑） */
  private static final Pattern P_HEX_ENCODING =
      Pattern.compile("0[xX][0-9a-fA-F]{2,}");

  static {
    // STANDARD 模式规则集
    STANDARD_PATTERNS.add(P_CLASSIC_OR);
    STANDARD_PATTERNS.add(P_DROP_TABLE);
    STANDARD_PATTERNS.add(P_DELETE_FROM);
    STANDARD_PATTERNS.add(P_UNION_SELECT);
    STANDARD_PATTERNS.add(P_UNION_SELECT_SIMPLE);
    STANDARD_PATTERNS.add(P_EXEC);
    STANDARD_PATTERNS.add(P_SP_EXEC);
    STANDARD_PATTERNS.add(P_LINE_COMMENT);
    STANDARD_PATTERNS.add(P_BLOCK_COMMENT);
    STANDARD_PATTERNS.add(P_ENCODING_FUNC);
    STANDARD_PATTERNS.add(P_HEX_ENCODING);

    // STRICT 模式在 STANDARD 基础上增加额外规则
    STRICT_PATTERNS.addAll(STANDARD_PATTERNS);
    STRICT_PATTERNS.add(P_CLASSIC_OR_EMPTY);
    STRICT_PATTERNS.add(P_UPDATE_SET);
  }

  private final SqlInjectionProperties properties;

  /**
   * SQL 注入检测过滤器入口。
   *
   * <p>遍历所有 query parameter 值，对每个非空值执行正则模式匹配。
   * 任一命中则告警 + 拒绝请求（400 错误，不暴露检测细节）。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（400）的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String method = request.getMethod().name();

    // 仅检测标准 HTTP 请求方法的 query parameters
    if (!isDetectionMethod(method)) {
      return chain.filter(exchange);
    }

    Map<String, String> queryParams = request.getURI().getQuery() != null
        ? parseQueryParameters(request)
        : Map.of();

    if (queryParams.isEmpty()) {
      return chain.filter(exchange);
    }

    String path = request.getURI().getPath();

    // 对每个参数值执行检测
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      String paramName = entry.getKey();
      String paramValue = entry.getValue();

      // 白名单参数不检测
      if (isWhitelisted(paramName)) {
        continue;
      }

      // 空值跳过
      if (paramValue == null || paramValue.isBlank()) {
        continue;
      }

      // 执行正则检测
      String matchedRule = detectInjection(paramValue);
      if (matchedRule != null) {
        alertSqlInjection(exchange, path, paramName, matchedRule);
        return rejectSqlInjection(exchange, path, paramName, matchedRule);
      }
    }

    return chain.filter(exchange);
  }

  /**
   * 解析请求查询参数。
   *
   * <p>从 {@link ServerHttpRequest} 的 query parameters map 提取键值对。
   *
   * @param request HTTP 请求
   * @return 查询参数键值映射
   */
  private Map<String, String> parseQueryParameters(ServerHttpRequest request) {
    return request.getQueryParams().toSingleValueMap();
  }

  /**
   * 判断参数名是否在白名单中。
   *
   * <p>白名单参数（如分页参数）不涉及 SQL 查询，跳过检测避免误报。
   *
   * @param paramName 参数名
   * @return true 表示该参数应跳过检测
   */
  private boolean isWhitelisted(String paramName) {
    return properties.getWhitelistParamNames() != null
        && properties.getWhitelistParamNames().contains(paramName);
  }

  /**
   * 执行 SQL 注入正则检测。
   *
   * <p>根据配置的 mode（STANDARD / STRICT）选择对应的规则集，
   * 任一模式命中即返回命中的规则名称（用于日志，不暴露给客户端）。
   *
   * @param value 待检测的参数值
   * @return 命中的规则名称，未命中返回 null
   */
  private String detectInjection(String value) {
    List<Pattern> patterns =
        "STRICT".equalsIgnoreCase(properties.getMode()) ? STRICT_PATTERNS : STANDARD_PATTERNS;

    for (Pattern pattern : patterns) {
      if (pattern.matcher(value).find()) {
        return pattern.pattern();
      }
    }
    return null;
  }

  /**
   * 发送 SQL 注入告警。
   *
   * <p>记录同步告警日志并异步发送 Sentry 告警（如果可用），
   * 同时记录命中的规则便于安全审计。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param path 请求路径
   * @param paramName 命中参数名
   * @param matchedRule 命中的规则
   */
  private void alertSqlInjection(
      ServerWebExchange exchange, String path, String paramName, String matchedRule) {
    String clientIp = GatewayIpUtils.getClientIp(exchange.getRequest());

    log.warn(
        "[SqlInjection] SQL 注入检测命中 path={} param={} ip={} rule={}",
        path,
        paramName,
        clientIp,
        matchedRule);

    SentryObservation.alert(
        AlertEvent.builder()
            .name("gateway.sql_injection.detected")
            .severity(AlertSeverity.P1)
            .summary("SQL 注入检测命中")
            .description("网关层 SQL 注入检测过滤器拦截可疑请求")
            .category("security")
            .labels(
                Map.of(
                    "ip", clientIp,
                    "path", path,
                    "param_name", paramName,
                    "matched_rule", matchedRule))
            .build());
  }

  /**
   * 拒绝 SQL 注入请求，返回 400 错误响应。
   *
   * <p>响应内容不包含检测细节（避免被攻击者探测规则），
   * 仅返回统一错误码和通用提示。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param path 请求路径（仅用于日志）
   * @param paramName 命中参数名（仅用于日志）
   * @param matchedRule 命中的规则（仅用于日志）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectSqlInjection(
      ServerWebExchange exchange, String path, String paramName, String matchedRule) {
    log.warn(
        "[SqlInjection] 拒绝请求 path={} param={} rule={}",
        path,
        paramName,
        matchedRule);
    return GatewayErrorWriter.write(
        exchange,
        HttpStatus.BAD_REQUEST,
        GatewayErrorCode.SQL_INJECTION_DETECTED,
        "error.SQL_INJECTION_DETECTED");
  }

  /**
   * 判断 HTTP 方法是否需要做 SQL 注入检测。
   *
   * @param method HTTP 方法名
   * @return true 表示该方法需要检测
   */
  private boolean isDetectionMethod(String method) {
    return "GET".equals(method)
        || "POST".equals(method)
        || "PUT".equals(method)
        || "PATCH".equals(method)
        || "DELETE".equals(method);
  }

  /**
   * 过滤器顺序：在请求体校验(4)之后执行，在认证(10)之前。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.SQL_INJECTION.getOrder();
  }
}
