package com.njydsz.agent.infra.tool;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.common.json.YdszJson;

/**
 * Text2SQL 工具（自然语言转 SQL 查询）
 *
 * <p>将用户的自然语言查询转换为 SQL 语句并执行，返回结构化结果。
 * 包含多重安全护栏：
 * <ul>
 *   <li>仅允许 SELECT 语句（禁止 INSERT/UPDATE/DELETE/DROP/ALTER）</li>
 *   <li>SQL 注入检测（禁止多语句、注释攻击）</li>
 *   <li>结果集大小限制（最多 100 行）</li>
 *   <li>执行超时控制（通过 {@link JdbcTemplate#setQueryTimeout}）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：无状态工具，依赖注入的 {@link JdbcTemplate} 和 {@link LlmClient} 需线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class Text2SqlTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(Text2SqlTool.class);

    /** 系统提示词：指导 LLM 生成安全的 SQL */
    private static final String SQL_GEN_SYSTEM_PROMPT = """
            你是一个 SQL 生成助手。根据用户问题和数据库表结构，生成安全的 PostgreSQL SELECT 查询。

            规则：
            1. 只生成 SELECT 查询，禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE
            2. 禁止使用分号多行语句
            3. 禁止使用 SQL 注释（-- 或 /*）
            4. 必须使用参数化占位符（?）而非字符串拼接
            5. 结果超过 100 行时必须加 LIMIT 100
            6. 只输出纯 SQL 语句，不要任何解释或 Markdown 格式

            数据库表结构：
            - ydsz_project (id, name, status, created_at, updated_at) — 项目表
            - ydsz_task (id, project_id, title, status, assignee, priority, created_at) — 任务表
            - ydsz_user (id, username, real_name, department, status) — 用户表
            - ydsz_approval (id, applicant_id, type, status, created_at) — 审批表
            """;

    /** 单次查询最大返回行数 */
    private static final int MAX_RESULT_ROWS = 100;

    /** SQL 执行超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final LlmClient llmClient;
    private final String defaultModel;

    public Text2SqlTool(JdbcTemplate jdbcTemplate, LlmClient llmClient, String defaultModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.llmClient = llmClient;
        this.defaultModel = defaultModel;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String question = arguments != null ? String.valueOf(arguments.getOrDefault("question", "")) : "";
        if (question.isBlank()) {
            return YdszJson.toJson(Map.of("error", "参数 'question' 不能为空"));
        }

        // Step 1: 使用 LLM 生成 SQL
        String sql = generateSql(question);
        if (sql == null || sql.isBlank()) {
            return YdszJson.toJson(Map.of("error", "无法生成有效的 SQL 查询"));
        }
        log.info("[Text2SQL] 生成 SQL: {}", sql);

        // Step 2: 安全校验
        String validationError = validateSql(sql);
        if (validationError != null) {
            log.warn("[Text2SQL] SQL 安全校验失败: {}", validationError);
            return YdszJson.toJson(Map.of("error", "SQL 安全校验失败: " + validationError));
        }

        // Step 3: 执行查询
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Map<String, Object>> limitedRows = rows.size() > MAX_RESULT_ROWS
                    ? rows.subList(0, MAX_RESULT_ROWS)
                    : rows;
            log.info("[Text2SQL] 查询完成: {} 行", limitedRows.size());
            return YdszJson.toJson(Map.of(
                    "sql", sql,
                    "rowCount", limitedRows.size(),
                    "data", limitedRows));
        } catch (Exception e) {
            log.error("[Text2SQL] SQL 执行失败: {}", e.getMessage());
            return YdszJson.toJson(Map.of(
                    "error", "SQL 执行失败: " + e.getMessage(),
                    "sql", sql));
        }
    }

    /**
     * 使用 LLM 将自然语言转换为 SQL。
     *
     * @param question 用户自然语言问题
     * @return 生成的 SQL 语句；生成失败时返回 null
     */
    private String generateSql(String question) {
        ChatRequest request = ChatRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        ChatMessage.system(SQL_GEN_SYSTEM_PROMPT),
                        ChatMessage.user(question, null)))
                .temperature(0.0)
                .maxTokens(500)
                .build();

        try {
            ChatResponse response = llmClient.chat(request);
            if (response != null && response.getContent() != null) {
                return response.getContent().trim()
                        .replaceAll("```sql", "")
                        .replaceAll("```", "")
                        .trim();
            }
        } catch (Exception e) {
            log.error("[Text2SQL] LLM 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * SQL 安全校验。
     *
     * <p>检查项：
     * <ul>
     *   <li>必须以 SELECT 开头</li>
     *   <li>禁止包含危险关键字（INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE）</li>
     *   <li>禁止多语句（分号）</li>
     *   <li>禁止 SQL 注释</li>
     * </ul>
     *
     * @param sql 待校验的 SQL
     * @return 校验失败原因；通过时返回 null
     */
    private String validateSql(String sql) {
        String upperSql = sql.toUpperCase().trim();

        // 必须以 SELECT 开头
        if (!upperSql.startsWith("SELECT")) {
            return "只允许 SELECT 查询";
        }

        // 禁止危险关键字
        String[] forbiddenKeywords = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CREATE", "GRANT", "REVOKE"};
        for (String keyword : forbiddenKeywords) {
            if (upperSql.contains(keyword)) {
                return "包含禁止的关键字: " + keyword;
            }
        }

        // 禁止多语句（分号）
        if (sql.contains(";")) {
            return "禁止多语句（分号）";
        }

        // 禁止 SQL 注释
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            return "禁止 SQL 注释";
        }

        return null;
    }
}
