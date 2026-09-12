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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.Text2SQLService;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.text2sql.SchemaRecallService;
import com.njydsz.agent.domain.text2sql.SemanticConsistencyChecker;
import com.njydsz.agent.domain.text2sql.TableSchema;
import com.njydsz.agent.domain.text2sql.Text2SQLEnhancedResult;
import com.njydsz.agent.domain.text2sql.Text2SqlStateContext;

/**
 * Text2SQL 增强实现（StateGraph 五步编排）。
 *
 * <p>在原有 Schema-Aware Prompt + LLM 生成 SQL + 安全护栏 + JDBC 执行基础上，
 * 新增 SchemaRecall → FeasibilityAssessment → SemanticConsistency 三阶段增强链路。
 *
 * <p>编排流程：
 *
 * <ol>
 *   <li>Schema Recall：根据用户问题智能召回相关表 Schema（失败降级到全量 Schema）
 *   <li>Feasibility Assessment：LLM 判断问题在当前 Schema 下是否可回答（未启用则跳过）
 *   <li>SQL Generation：LLM 生成 SQL（复用安全校验模式与 SQL 执行逻辑）
 *   <li>Semantic Consistency：LLM 二次校验 SQL 与用户意图是否匹配（低于阈值则拒绝执行）
 *   <li>Execution：安全校验 + 执行 SQL
 * </ol>
 *
 * <p>通过 {@code @ConditionalOnProperty} 控制是否启用：当配置 {@code ydsz.agent.text2sql.enhanced=true}
 * 时激活，否则回退到 {@link JdbcText2SQLService}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Primary
@Service
@ConditionalOnProperty(
    prefix = "ydsz.agent.text2sql",
    name = "enhanced",
    havingValue = "true",
    matchIfMissing = false)
public class EnhancedJdbcText2SQLService implements Text2SQLService {

  /** 默认匹配得分（无明确匹配时的中性分值） */
  private static final double DEFAULT_MATCH_SCORE = 0.5;
  /** SQL 拼接缓冲区初始容量 */
  private static final int SQL_BUFFER_CAPACITY = 256;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 结果行数上限 */
  private static final int MAX_RESULT_ROWS = 100;

  /** SQL 执行超时（秒） */
  private static final int EXEC_TIMEOUT_SECONDS = 10;

  /** SQL 生成请求最大输出 Token 数 */
  private static final int SQL_MAX_TOKENS = 512;

  /** 可行性评估最大输出 Token 数 */
  private static final int FEASIBILITY_MAX_TOKENS = 256;

  /** 错误信息中 SQL 片段的截断长度 */
  private static final int SQL_SNIPPET_LENGTH = 50;

  /** 可行性通过阈值（≥ 0.5 视为可回答） */
  private static final double FEASIBILITY_PASS_THRESHOLD = 0.5;

  /** 允许的 SQL 开头（仅 SELECT / WITH） */
  private static final Set<String> ALLOWED_PREFIXES = Set.of("SELECT", "WITH");

  /** SQL 注入危险模式（拒绝匹配） */
  private static final List<Pattern> INJECTION_PATTERNS =
      List.of(
          Pattern.compile("--.*$", Pattern.MULTILINE),
          Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL),
          Pattern.compile(
              ";\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|EXEC|CALL)",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile(
              "\\b(UNION\\s+ALL\\s+SELECT|INTO\\s+OUTFILE|LOAD_FILE|BENCHMARK|SLEEP)\\b",
              Pattern.CASE_INSENSITIVE));

  /** tenantId 格式校验正则（仅允许字母数字和下划线） */
  private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private final LlmClient llmClient;
  private final DataSource dataSource;
  private final SchemaRecallService schemaRecallService;
  private final SemanticConsistencyChecker consistencyChecker;
  private final String defaultModel;
  private final boolean schemaRecallEnabled;
  private final int schemaRecallMaxTables;
  private final boolean feasibilityCheckEnabled;
  private final boolean consistencyCheckEnabled;
  private final double consistencyThreshold;

  /**
   * 构造增强版 Text2SQL 服务（五步 StateGraph 编排）。
   *
   * @param llmClient LLM 客户端
   * @param dataSource 数据源（用于执行 SQL）
   * @param schemaRecallService Schema 召回服务
   * @param consistencyChecker 语义一致性校验器
   * @param defaultModel LLM 模型名称
   * @param enhancedEnabled 增强模式是否启用
   * @param schemaRecallEnabled Schema 召回步骤是否启用
   * @param schemaRecallMaxTables 最大召回表数量
   * @param feasibilityCheckEnabled 可行性评估步骤是否启用
   * @param consistencyCheckEnabled 语义一致性校验步骤是否启用
   * @param consistencyThreshold 语义一致性通过阈值（≥ 该值才执行 SQL）
   */
  public EnhancedJdbcText2SQLService(
      LlmClient llmClient,
      DataSource dataSource,
      SchemaRecallService schemaRecallService,
      SemanticConsistencyChecker consistencyChecker,
      @Value("${ydsz.agent.llm.default-model:gpt-4o-mini}") String defaultModel,
      @Value("${ydsz.agent.text2sql.enhanced:false}") boolean enhancedEnabled,
      @Value("${ydsz.agent.text2sql.schema-recall-enabled:true}") boolean schemaRecallEnabled,
      @Value("${ydsz.agent.text2sql.schema-recall-max-tables:5}") int schemaRecallMaxTables,
      @Value("${ydsz.agent.text2sql.feasibility-check-enabled:true}")
          boolean feasibilityCheckEnabled,
      @Value("${ydsz.agent.text2sql.consistency-check-enabled:true}")
          boolean consistencyCheckEnabled,
      @Value("${ydsz.agent.text2sql.consistency-threshold:0.7}") double consistencyThreshold) {
    this.llmClient = llmClient;
    this.dataSource = dataSource;
    this.schemaRecallService = schemaRecallService;
    this.consistencyChecker = consistencyChecker;
    this.defaultModel = defaultModel;
    this.schemaRecallEnabled = schemaRecallEnabled;
    this.schemaRecallMaxTables = schemaRecallMaxTables;
    this.feasibilityCheckEnabled = feasibilityCheckEnabled;
    this.consistencyCheckEnabled = consistencyCheckEnabled;
    this.consistencyThreshold = consistencyThreshold;
  }

  /**
   * 执行增强查询（委托给 {@link #queryEnhanced} 后转换为基类结果）。
   *
   * @param naturalLanguageQuery 用户自然语言查询
   * @param tenantId 租户 ID
   * @return 基础查询结果
   * @throws Text2SQLException 任一编排步骤失败
   */
  @Override
  public Text2SQLResult query(String naturalLanguageQuery, String tenantId) throws Text2SQLException {
    return queryEnhanced(naturalLanguageQuery, tenantId).toBaseResult();
  }

  /**
   * 执行完整的五步增强查询链路（Schema Recall → Feasibility → SQL Gen → Consistency → Execution）。
   *
   * @param naturalLanguageQuery 用户自然语言查询
   * @param tenantId 租户 ID（用于行级隔离）
   * @return 增强查询结果（含召回表、可行性分数、一致性分数等诊断信息）
   * @throws Text2SQLException 任一编排步骤失败或一致性校验未通过
   */
  @Override
  public Text2SQLEnhancedResult queryEnhanced(String naturalLanguageQuery, String tenantId)
      throws Text2SQLException {
    Text2SqlStateContext context =
        Text2SqlStateContext.builder(naturalLanguageQuery, tenantId).build();
    try {
      // Step 1: Schema Recall
      context = executeSchemaRecall(context);
      // Step 2: Feasibility Assessment
      context = executeFeasibilityAssessment(context);
      // Step 3: SQL Generation
      context = executeSqlGeneration(context);
      // Step 4: Semantic Consistency
      context = executeSemanticConsistency(context);
      // Step 5: Execution
      return executeWithEnhancedResult(context);
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new Text2SQLException("增强链路执行异常: " + e.getMessage(), "TEXT2SQL_ENHANCED_ERROR", e);
    }
  }

  /**
   * 执行 Schema 召回节点。
   *
   * <p>若 Schema 召回功能未启用或召回失败，降级使用全量 Schema 列表。
   *
   * @param context 当前上下文
   * @return 更新后的上下文
   */
  private Text2SqlStateContext executeSchemaRecall(Text2SqlStateContext context) {
    if (!schemaRecallEnabled) {
      log.debug("[Text2SQL:SchemaRecall] 未启用，跳过");
      return context;
    }
    try {
      // 当前使用全量 Schema（实际项目中可注入 TableSchemaProvider）
      List<TableSchema> allSchemas = loadAllSchemas();
      List<TableSchema> recalled =
          schemaRecallService.recallRelevantSchemas(
              context.getQuery(), allSchemas, schemaRecallMaxTables);
      log.info("[Text2SQL:SchemaRecall] 召回表数量={}", recalled.size());
      return Text2SqlStateContext.builder(context.getQuery(), context.getTenantId())
          .recalledSchemas(recalled)
          .build();
    } catch (Exception e) {
      log.warn("[Text2SQL:SchemaRecall] 召回失败，降级到全量 Schema: {}", e.getMessage());
      return context;
    }
  }

  /**
   * 执行可行性评估节点。
   *
   * <p>使用 LLM 判断用户问题在已召回 Schema 下是否可回答。
   *
   * @param context 当前上下文
   * @return 更新后的上下文
   * @throws Text2SQLException 问题不可回答
   */
  private Text2SqlStateContext executeFeasibilityAssessment(Text2SqlStateContext context)
      throws Text2SQLException {
    if (!feasibilityCheckEnabled) {
      log.debug("[Text2SQL:Feasibility] 未启用，跳过");
      return context;
    }
    try {
      String schemasDesc = buildSchemasDescription(context);
      String prompt =
          """
          你是数据库分析助手。根据以下表结构和用户问题，判断能否用 SQL 回答该问题。

          可用表结构：
          %s

          用户问题：%s

          请按以下 JSON 格式回答（不要包含其他内容）：
          {"score": <0.0到1.0之间的小数，1.0表示完全可以回答>, "reasoning": "<简要说明理由>"}
          """
              .formatted(schemasDesc, context.getQuery());

      ChatRequest request =
          ChatRequest.builder()
              .model(defaultModel)
              .messages(List.of(ChatMessage.user(prompt, null)))
              .temperature(0)
              .maxTokens(FEASIBILITY_MAX_TOKENS)
              .build();

      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      double score = DEFAULT_MATCH_SCORE;
      String reasoning = "可行性评估默认通过";
      if (content != null && !content.isBlank()) {
        try {
          int scoreStart = content.indexOf("\"score\"");
          if (scoreStart >= 0) {
            int colonIdx = content.indexOf(':', scoreStart);
            int commaIdx = content.indexOf(',', colonIdx);
            int braceIdx = content.indexOf('}', colonIdx);
            int endIdx;
            if (commaIdx > 0) {
              endIdx = commaIdx;
            } else if (braceIdx > 0) {
              endIdx = braceIdx;
            } else {
              endIdx = content.length();
            }
            if (colonIdx > 0 && endIdx > colonIdx) {
              score =
                  Double.parseDouble(content.substring(colonIdx + 1, endIdx).trim());
              score = Math.max(0.0, Math.min(1.0, score));
            }
          }
          int reasonStart = content.indexOf("\"reasoning\"");
          if (reasonStart > 0) {
            int reasonColon = content.indexOf(':', reasonStart);
            if (reasonColon > 0) {
              int reasonEnd = content.indexOf('}', reasonColon);
              if (reasonEnd > reasonColon) {
                reasoning =
                    content.substring(reasonColon + 1, reasonEnd).trim()
                        .replaceAll("^\"|\"$", "");
              }
            }
          }
        } catch (NumberFormatException e) {
          log.warn("[Text2SQL:Feasibility] 解析分数失败，使用默认值: {}", e.getMessage());
        }
      }
      log.info("[Text2SQL:Feasibility] 评估完成: score={}, reasoning='{}'", score, reasoning);

      if (score < FEASIBILITY_PASS_THRESHOLD) {
        throw new Text2SQLException(
            "问题在当前 Schema 下不可回答: " + reasoning,
            "TEXT2SQL_NOT_FEASIBLE");
      }
      return Text2SqlStateContext.builder(context.getQuery(), context.getTenantId())
          .recalledSchemas(context.getRecalledSchemas())
          .feasibilityScore(score)
          .feasibilityReason(reasoning)
          .build();
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[Text2SQL:Feasibility] 评估异常，默认通过: {}", e.getMessage());
      return context;
    }
  }

  /**
   * 执行 SQL 生成节点。
   *
   * @param context 当前上下文
   * @return 更新后的上下文
   * @throws Text2SQLException SQL 生成失败
   */
  private Text2SqlStateContext executeSqlGeneration(Text2SqlStateContext context)
      throws Text2SQLException {
    validateTenantId(context.getTenantId());
    String schemasDesc = buildSchemasDescription(context);
    String prompt =
        """
        你是 SQL 生成助手。根据以下表结构生成 PostgreSQL SELECT 查询语句。

        表结构：
        %s

        用户问题：%s

        规则：
        1. 仅生成 SELECT / WITH 查询，禁止任何 DML/DDL 操作
        2. SQL 必须包含租户隔离条件：WHERE tenant_id = ?
        3. 结果不超过 %d 行（使用 LIMIT）
        4. 仅输出纯 SQL，不要包裹在代码块中
        """
            .formatted(schemasDesc, context.getQuery(), MAX_RESULT_ROWS);

    ChatRequest request =
        ChatRequest.builder()
            .model(defaultModel)
            .messages(List.of(ChatMessage.user(prompt, null)))
            .temperature(0)
            .maxTokens(SQL_MAX_TOKENS)
            .build();
    try {
      ChatResponse response = llmClient.chat(request);
      String content = response.getContent();
      if (content == null || content.isBlank()) {
        throw new Text2SQLException("LLM 未返回 SQL", "TEXT2SQL_EMPTY_RESPONSE");
      }
      String sql = extractSql(content);
      // 添加租户隔离条件
      String sqlWithTenant = appendTenantCondition(sql, context.getTenantId());
      return Text2SqlStateContext.builder(context.getQuery(), context.getTenantId())
          .recalledSchemas(context.getRecalledSchemas())
          .generatedSql(sqlWithTenant)
          .feasibilityScore(context.getFeasibilityScore().orElse(null))
          .feasibilityReason(context.getFeasibilityReason().orElse(null))
          .build();
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new Text2SQLException("LLM 调用失败: " + e.getMessage(), "TEXT2SQL_LLM_ERROR", e);
    }
  }

  /**
   * 执行语义一致性校验节点。
   *
   * <p>使用 LLM 判断生成的 SQL 是否与用户意图匹配。若一致性分数低于阈值则拒绝执行。
   *
   * @param context 当前上下文
   * @return 更新后的上下文
   * @throws Text2SQLException 一致性不达标
   */
  private Text2SqlStateContext executeSemanticConsistency(Text2SqlStateContext context)
      throws Text2SQLException {
    if (!consistencyCheckEnabled) {
      log.debug("[Text2SQL:Consistency] 未启用，跳过");
      return context;
    }
    String sql = context.getGeneratedSql().orElse(null);
    if (sql == null) {
      return context;
    }
    try {
      SemanticConsistencyChecker.ConsistencyCheckResult result =
          consistencyChecker.check(context.getQuery(), sql);
      log.info(
          "[Text2SQL:Consistency] 校验完成: score={}, threshold={}, reasoning='{}'",
          result.score(),
          consistencyThreshold,
          result.reasoning());
      if (!result.isAboveThreshold(consistencyThreshold)) {
        throw new Text2SQLException(
            "语义一致性校验未通过（分数 %.2f < 阈值 %.2f）: %s"
                .formatted(result.score(), consistencyThreshold, result.reasoning()),
            "TEXT2SQL_CONSISTENCY_FAILED");
      }
      return Text2SqlStateContext.builder(context.getQuery(), context.getTenantId())
          .recalledSchemas(context.getRecalledSchemas())
          .generatedSql(sql)
          .feasibilityScore(context.getFeasibilityScore().orElse(null))
          .feasibilityReason(context.getFeasibilityReason().orElse(null))
          .consistencyScore(result.score())
          .consistencyReason(result.reasoning())
          .build();
    } catch (Text2SQLException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[Text2SQL:Consistency] 校验异常，默认通过: {}", e.getMessage());
      return context;
    }
  }

  /**
   * 执行 SQL 并返回增强结果。
   *
   * @param context 包含已生成 SQL 的上下文
   * @return 查询增强结果
   * @throws Text2SQLException 执行失败
   */
  private Text2SQLEnhancedResult executeWithEnhancedResult(Text2SqlStateContext context)
      throws Text2SQLException {
    String sql =
        context
            .getGeneratedSql()
            .orElseThrow(
                () -> new Text2SQLException("无法执行：未生成 SQL", "TEXT2SQL_NO_SQL"));
    validateSql(sql);
    Text2SQLResult baseResult = executeSql(sql);
    List<String> recalledTableNames = buildRecalledTableNames(context);
    return new Text2SQLEnhancedResult(
        baseResult.columns(),
        baseResult.rows(),
        baseResult.rowCount(),
        baseResult.generatedSql(),
        baseResult.executionTimeMs(),
        recalledTableNames,
        context.getFeasibilityReason().orElse(null),
        context.getConsistencyScore().orElse(null));
  }

  /**
   * SQL 安全校验（仅 SELECT、注入检测）。
   *
   * @param sql 待校验 SQL
   * @throws Text2SQLException 校验失败
   */
  private void validateSql(String sql) throws Text2SQLException {
    String trimmed = sql.trim().toUpperCase();
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
        List<Map<String, Object>> rows = new ArrayList<>(COLLECTION_CAPACITY);
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>(COLLECTION_CAPACITY);
          for (int i = 1; i <= colCount; i++) {
            row.put(columns.get(i - 1), rs.getObject(i));
          }
          rows.add(row);
        }
        long duration = System.currentTimeMillis() - start;
        log.info(
            "[Text2SQL:Execution] 执行完成: rows={}, duration={}ms, sql={}",
            rows.size(),
            duration,
            sql);
        return new Text2SQLResult(columns, rows, rows.size(), sql, duration);
      }
    } catch (Text2SQLException e) {
      log.warn("[Text2SQL:Execution] 执行失败: reason={}, sql={}", e.getMessage(), sql);
      throw e;
    } catch (Exception e) {
      log.error("[Text2SQL:Execution] 执行异常: reason={}, sql={}", e.getMessage(), sql);
      throw new Text2SQLException("SQL 执行失败: " + e.getMessage(), "TEXT2SQL_EXEC_ERROR", e);
    }
  }

  /**
   * 校验 tenantId 格式。
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
      return sql.replaceFirst("(?i)WHERE", "WHERE tenant_id = '" + tenantId + "' AND ");
    } else {
      return sql + " WHERE tenant_id = '" + tenantId + "'";
    }
  }

  /**
   * 从 LLM 响应中提取纯 SQL。
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
   * 构建表结构描述文本（供 LLM Prompt 使用）。
   *
   * @param context 状态上下文
   * @return Schema 文本描述
   */
  private String buildSchemasDescription(Text2SqlStateContext context) {
    List<TableSchema> schemas = context.getRecalledSchemas();
    if (schemas.isEmpty()) {
      return "（暂无可用表结构，请根据用户问题合理推断）";
    }
    StringBuilder sb = new StringBuilder(SQL_BUFFER_CAPACITY);
    for (TableSchema table : schemas) {
      sb.append("表名: ").append(table.tableName());
      if (table.description() != null && !table.description().isEmpty()) {
        sb.append("（").append(table.description()).append("）");
      }
      sb.append("\n");
      if (table.columns() != null && !table.columns().isEmpty()) {
        sb.append("  列：");
        List<String> colDescs = new ArrayList<>(table.columns().size());
        for (TableSchema.ColumnDefinition col : table.columns()) {
          String desc = col.name() + " " + col.type();
          if (col.description() != null && !col.description().isEmpty()) {
            desc += "（" + col.description() + "）";
          }
          colDescs.add(desc);
        }
        sb.append(String.join(", ", colDescs));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * 加载全量表 Schema（当前版本返回空列表，实际项目中应从数据库元数据加载或配置注入）。
   *
   * @return 全量表 Schema
   */
  private List<TableSchema> loadAllSchemas() {
    // TODO: 从数据库元数据或配置中加载
    return List.of();
  }

  /**
   * 组装所有字段名称列表。
   *
   * @param context 上下文
   * @return 已召回表名列表
   */
  private static List<String> buildRecalledTableNames(Text2SqlStateContext context) {
    return context.getRecalledSchemas().stream().map(TableSchema::tableName).toList();
  }
}
