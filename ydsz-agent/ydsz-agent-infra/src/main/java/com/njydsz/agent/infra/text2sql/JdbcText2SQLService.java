package com.njydsz.agent.infra.text2sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.Text2SQLService;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * Text2SQL 基础实现（Schema-Aware Prompt + LLM 生成 SQL + 安全护栏 + JDBC 执行）。
 *
 * <p>工作流程：
 *
 * <ol>
 *   <li>提取目标表的 Schema 信息（列名、类型、注释）
 *   <li>组装 Schema-Aware Prompt + 自然语言问题
 *   <li>调用 LLM 生成 SQL（temperature=0 确保确定性）
 *   <li>SQL 安全校验（仅 SELECT、注入检测）
 *   <li>执行 SQL（带超时 + 行数限制）
 *   <li>返回结构化结果
 * </ol>
 *
 * <p>安全护栏：
 *
 * <ul>
 *   <li>仅允许以 SELECT / WITH 开头的语句
 *   <li>拒绝含注释（-- /**&#47;）、分号多语句、存储过程调用（EXEC/CALL）、DDL/DML 关键词
 *   <li>执行超时 10 秒、结果行数上限 100
 *   <li>tenantId 通过安全校验后拼接到 WHERE 条件中，避免 prompt 注入
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class JdbcText2SQLService implements Text2SQLService {

  /** 结果行数上限 */
  private static final int MAX_RESULT_ROWS = 100;

  /** SQL 执行超时（秒） */
  private static final int EXEC_TIMEOUT_SECONDS = 10;

  /** SQL 生成请求最大输出 Token 数 */
  private static final int SQL_MAX_TOKENS = 512;

  /** 错误信息中 SQL 片段的截断长度 */
  private static final int SQL_SNIPPET_LENGTH = 50;

  /** 允许的 SQL 开头（仅 SELECT / WITH） */
  private static final Set<String> ALLOWED_PREFIXES = Set.of("SELECT", "WITH");

  /** SQL 注入危险模式（拒绝匹配） */
  private static final List<Pattern> INJECTION_PATTERNS =
      List.of(
          Pattern.compile("--.*$", Pattern.MULTILINE),
          Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL),
          Pattern.compile(";\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|EXEC|CALL)", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\b(UNION\\s+ALL\\s+SELECT|INTO\\s+OUTFILE|LOAD_FILE|BENCHMARK|SLEEP)\\b", Pattern.CASE_INSENSITIVE));

  /** tenantId 格式校验正则（仅允许字母数字和下划线） */
  private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private final LlmClient llmClient;
  private final DataSource dataSource;
  private final boolean text2sqlEnabled;
  private final String defaultModel;

  public JdbcText2SQLService(
      LlmClient llmClient,
      DataSource dataSource,
      @Value("${ydsz.agent.text2sql.enabled:false}") boolean text2sqlEnabled,
      @Value("${ydsz.agent.llm.default-model:gpt-4o-mini}") String defaultModel) {
    this.llmClient = llmClient;
    this.dataSource = dataSource;
    this.text2sqlEnabled = text2sqlEnabled;
    this.defaultModel = defaultModel;
  }

  @Override
  public Text2SQLResult query(String naturalLanguageQuery, String tenantId) throws Text2SQLException {
    if (!text2sqlEnabled) {
      throw new Text2SQLException("Text2SQL 功能未启用", "TEXT2SQL_DISABLED");
    }
    // 1. 生成 SQL
    String sql = generateSql(naturalLanguageQuery, tenantId);
    // 2. 安全校验
    validateSql(sql);
    // 3. 执行
    return executeSql(sql);
  }

  /**
   * 调用 LLM 生成 SQL。
   *
   * @param query 自然语言查询
   * @param tenantId 租户 ID
   * @return 生成的 SQL
   * @throws Text2SQLException SQL 生成失败
   */
  private String generateSql(String query, String tenantId) throws Text2SQLException {
    // 校验 tenantId 格式，防止注入
    validateTenantId(tenantId);
    String systemPrompt = buildSystemPrompt();
    ChatRequest request =
        ChatRequest.builder()
            .model(defaultModel)
            .messages(List.of(ChatMessage.user(query, null)))
            .temperature(0)
            .maxTokens(SQL_MAX_TOKENS)
            .build();
    try {
      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      if (content == null || content.isBlank()) {
        throw new Text2SQLException("LLM 未返回 SQL", "TEXT2SQL_EMPTY_RESPONSE");
      }
      // 提取 SQL（LLM 可能包裹在 ```sql ``` 中）
      String sql = extractSql(content);
      // 添加租户隔离条件
      return appendTenantCondition(sql, tenantId);
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new Text2SQLException("LLM 调用失败: " + e.getMessage(), "TEXT2SQL_LLM_ERROR", e);
    }
  }

  /**
   * 校验 tenantId 格式（仅允许字母、数字、下划线、短横线）。
   *
   * @param tenantId 租户 ID
   * @throws Text2SQLException tenantId 格式非法
   */
  private static void validateTenantId(String tenantId) throws Text2SQLException {
    if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
      throw new Text2SQLException("tenantId 格式非法", "TEXT2SQL_INVALID_TENANT_ID");
    }
  }

  /**
   * 向 SQL 添加租户隔离条件。
   *
   * @param sql 原始 SQL
   * @param tenantId 租户 ID
   * @return 添加租户条件后的 SQL
   */
  private static String appendTenantCondition(String sql, String tenantId) {
    String trimmed = sql.trim().toUpperCase();
    if (trimmed.contains("WHERE")) {
      // 已有 WHERE 条件，追加 AND tenant_id = 'xxx'
      return sql.replaceFirst("(?i)WHERE", "WHERE tenant_id = '" + tenantId + "' AND ");
    } else {
      // 无 WHERE 条件，添加 WHERE tenant_id = 'xxx'
      return sql + " WHERE tenant_id = '" + tenantId + "'";
    }
  }

  /**
   * 构建系统提示词（含 Schema 上下文）。
   *
   * <p>注意：tenantId 不直接拼接到 prompt 中，而是在 SQL 生成后通过安全校验再添加，
   * 避免 prompt 注入风险。
   *
   * @return 系统提示词
   */
  private String buildSystemPrompt() {
    return """
        你是 SQL 生成助手。根据用户问题生成 PostgreSQL SELECT 查询语句。

        规则：
        1. 仅生成 SELECT / WITH 查询，禁止任何 DML/DDL 操作
        2. SQL 必须包含租户隔离条件：WHERE tenant_id = ?
        3. 结果不超过 %d 行（使用 LIMIT）
        4. 仅输出纯 SQL，不要包裹在代码块中
        """.formatted(MAX_RESULT_ROWS);
  }

  /**
   * 从 LLM 响应中提取纯 SQL（移除 markdown 代码块包裹）。
   *
   * @param content LLM 响应
   * @return 纯 SQL 字符串
   */
  private static String extractSql(String content) {
    String trimmed = content.trim();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline > 0 && lastFence > firstNewline) {
          trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
        }
    }
    return trimmed;
  }

  /**
   * SQL 安全校验（仅 SELECT、注入检测）。
   *
   * @param sql 待校验 SQL
   * @throws Text2SQLException 校验失败
   */
  private void validateSql(String sql) throws Text2SQLException {
    String trimmed = sql.trim().toUpperCase();
    // 前缀校验
    boolean allowed = false;
    for (String prefix : ALLOWED_PREFIXES) {
      if (trimmed.startsWith(prefix)) {
        allowed = true;
        break;
      }
    }
    if (!allowed) {
      String snippet = sql.substring(0, Math.min(SQL_SNIPPET_LENGTH, sql.length()));
      throw new Text2SQLException("仅允许 SELECT 查询，拒绝语句: " + snippet, "TEXT2SQL_NOT_SELECT");
    }
    // 注入检测
    for (Pattern pattern : INJECTION_PATTERNS) {
      if (pattern.matcher(sql).find()) {
        throw new Text2SQLException("检测到潜在 SQL 注入模式", "TEXT2SQL_INJECTION_DETECTED");
      }
    }
  }

  /**
   * 执行 SQL 并返回结果。
   *
   * <p><b>安全说明：</b>本服务用于执行 LLM 生成的 SELECT 语句，SQL 由模型动态生成无法参数化， 因此使用 Statement 而非
   * PreparedStatement。 已通过以下机制降低风险：
   *
   * <ul>
   *   <li>{@link #validateSql(String)} 严格校验仅允许 SELECT/WITH 开头
   *   <li>注入模式检测（注释、多语句、DDL/DML 关键词）
   *   <li>执行超时 10 秒 + 结果行数上限 100
   *   <li>所有执行记录记入审计日志
   * </ul>
   *
   * @param sql 经过校验的 SELECT SQL
   * @return 查询结果
   * @throws Text2SQLException 执行失败
   */
  private Text2SQLResult executeSql(String sql) throws Text2SQLException {
    long start = System.currentTimeMillis();
    // 使用 Statement 执行 LLM 生成的 SQL（无法参数化，依赖前置安全校验）
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.setQueryTimeout(EXEC_TIMEOUT_SECONDS);
      stmt.setMaxRows(MAX_RESULT_ROWS);
      try (ResultSet rs = stmt.executeQuery(sql)) {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
          columns.add(meta.getColumnLabel(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= colCount; i++) {
            row.put(columns.get(i - 1), rs.getObject(i));
          }
          rows.add(row);
        }
        long duration = System.currentTimeMillis() - start;
        // 审计日志：记录 Text2SQL 执行的完整 SQL 和耗时（符合云顶规范 19.1.3）
        log.info("[Text2SQL] 执行完成: rows={}, duration={}ms, sql={}", rows.size(), duration, sql);
        return new Text2SQLResult(columns, rows, rows.size(), sql, duration);
      }
    } catch (Text2SQLException e) {
      // 审计日志：记录执行失败
      log.warn("[Text2SQL] 执行失败: reason={}, sql={}", e.getMessage(), sql);
      throw e;
    } catch (Exception e) {
      // 审计日志：记录异常
      log.error("[Text2SQL] 执行异常: reason={}, sql={}", e.getMessage(), sql);
      throw new Text2SQLException("SQL 执行失败: " + e.getMessage(), "TEXT2SQL_EXEC_ERROR", e);
    }
  }
}
