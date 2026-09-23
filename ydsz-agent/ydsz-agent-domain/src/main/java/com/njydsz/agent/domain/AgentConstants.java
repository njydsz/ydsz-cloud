package com.njydsz.agent.domain;

/**
 * 全模块共享常量。
 *
 * <p>集中管理 Agent 模块中的魔法数值，消除散落在各处的重复字面量。
 * 常量按作用域分组命名，便于定位和理解。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public final class AgentConstants {

  /** 私有构造器防止实例化 */
  private AgentConstants() {
  }

  // ======================== 集合初始化 ========================

  /** 集合默认初始容量（List/Map 在无明确大小时使用） */
  public static final int COLLECTION_CAPACITY = 16;

  // ======================== SSE 流式 ========================

  /** SSE 流式超时（毫秒），默认 2 分钟 */
  public static final long SSE_TIMEOUT_MS = 120_000L;

  /** SSE 心跳间隔（秒），每 15 秒发送 keep-alive 注释帧 */
  public static final long SSE_HEARTBEAT_INTERVAL_SECONDS = 15L;

  /** SSE 推文体截断阈值（字符数），日志中超过此长度的内容将被截断 */
  public static final int SSE_LOG_TRUNCATE_LENGTH = 2048;

  // ======================== 执行器超时 ========================

  /** 子 Agent 执行超时（秒），用于 Supervisor 子任务 */
  public static final long SUB_TASK_TIMEOUT_SECONDS = 120;

  /** DAG 子节点默认超时（秒） */
  public static final int DAG_NODE_TIMEOUT_SECONDS = 60;

  /** DAG 整图总超时（秒），默认 5 分钟 */
  public static final int DAG_TOTAL_TIMEOUT_SECONDS = 300;

  // ======================== Cron Trigger ========================

  /** Cron 触发器最小执行间隔（秒），防止同一分钟内多次触发 */
  public static final long CRON_MIN_EXECUTION_INTERVAL_SECONDS = 55;

  // ======================== Text2SQL ========================

  /** Text2SQL 结果行数上限 */
  public static final int TEXT2SQL_MAX_RESULT_ROWS = 100;

  /** Text2SQL SQL 执行超时（秒） */
  public static final int TEXT2SQL_EXEC_TIMEOUT_SECONDS = 10;

  /** Text2SQL Schema 默认缓存时间（毫秒），默认 5 分钟 */
  public static final long TEXT2SQL_SCHEMA_CACHE_TTL_MS = 300_000L;

  // ======================== Agent 执行 ========================

  /** Agent 默认最大迭代轮次（ReAct/Plan 兜底） */
  public static final int DEFAULT_MAX_ITERATIONS = 10;

  /** 规划阶段最大输出 Token 数（Supervisor / PlanExecute） */
  public static final int PLAN_MAX_TOKENS = 500;

  /** 规划阶段温度（确保稳定输出） */
  public static final double PLAN_TEMPERATURE = 0.3;

  /** 汇总阶段温度 */
  public static final double SYNTHESIZE_TEMPERATURE = 0.5;

  // ======================== JSON 解析 ========================

  /** JSON 解析时数字字符串的前导/尾随字符清理正则 */
  public static final String JSON_NUMBER_CLEAN_REGEX = "^\"|\"$";
}
