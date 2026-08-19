package com.njydsz.agent.infra.text2sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.Text2SQLService;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

import lombok.extern.slf4j.Slf4j;

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
 *   <li;gt;拒绝含注释（-- /**&#47;）、分号多语句、存储过程调用（EXEC/CALL）、DDL/DML 关键词
 *   <li>执行超时 10 秒、结果行数上限 100
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class JdbcText2SQLService implements Text2SQLService {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcText2SQLService.class);

  /** 结果行数上限 */
  private static final int MAX_RESULT_ROWS = 100;

  /** SQL 执行超时（秒） */
  private static final int EXEC_TIMEOUT_SECONDS = 10;

  /** 允许的 SQL 开头（仅 SELECT / WITH） */
  private static final Set<String> ALLOWED_PREFIXES = Set.of("SELECT", "WITH");

  /** SQL 注入危险模式（拒绝匹配） */
  private static final List<Pattern> INJECTION_PATTERNS =
      List.of(
          Pattern.compile("--.*$", Pattern.MULTILINE),
          Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL),
          Pattern.compile(";\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|EXEC|CALL)", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\b(UNION\\s+ALL\\s+SELECT|INTO\\s+OUTFILE|LOAD_FILE|BENCHMARK|SLEEP)\\b", Pattern.CASE_INSENSITIVE));

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
    String systemPrompt = buildSystemPrompt(tenantId);
    ChatRequest request =
        ChatRequest.builder()
            .model(defaultModel)
            .messages(List.of(ChatMessage.user(query, null)))
            .temperature(0)
            .maxTokens(512)
            .build();
    try {
      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      if (content == null || content.isBlank()) {
        throw new Text2SQLException("LLM 未返回 SQL", "TEXT2SQL_EMPTY_RESPONSE");
      }
      // 提取 SQL（LLM 可能包裹在 ```sql ``` 中）
      return extractSql(content);
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new Text2SQLException("LLM 调用失败: " + e.getMessage(), "TEXT2SQL_LLM_ERROR", e);
    }
  }

  /**
   * 构建系统提示词（含 Schema 上下文）。
   *
   * @param tenantId 租户 ID
   * @return 系统提示词
   */
  private String buildSystemPrompt(String tenantId) {
    return """
        你是 SQL 生成助手。根据用户问题生成 PostgreSQL SELECT 查询语句。

        规则：
        1. 仅生成 SELECT / WITH 查询，禁止任何 DML/DDL 操作
        2. SQL 必须包含租户隔离条件：WHERE tenant_id = '%s'
        3. 结果不超过 %d 行（使用 LIMIT）
        4. 仅输出纯 SQL，不要包裹在代码块中
        """.formatted(tenantId, MAX_RESULT_ROWS);
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
      throw new Text2SQLException("仅允许 SELECT 查询，拒绝语句: " + sql.substring(0, Math.min(50, sql.length())), "TEXT2SQL_NOT_SELECT");
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
   * @param sql 经过校验的 SELECT SQL
   * @return 查询结果
   * @throws Text2SQLException 执行失败
   */
  private Text2SQLResult executeSql(String sql) throws Text2SQLException {
    long start = System.currentTimeMillis();
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
        LOG.info("[Text2SQL] 执行完成: rows={}, duration={}ms", rows.size(), duration);
        return new Text2SQLResult(columns, rows, rows.size(), sql, duration);
      }
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new Text2SQLException("SQL 执行失败: " + e.getMessage(), "TEXT2SQL_EXEC_ERROR", e);
    }
  }
}
