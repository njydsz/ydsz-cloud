package com.njydsz.agent.domain.asynctask;

/**
 * 异步任务类型枚举
 *
 * <p>定义系统内置的异步任务类型。业务模块可扩展自定义类型。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum AsyncTaskType {

  /** 洞察报告生成 */
  REPORT_GENERATE("REPORT_GENERATE", "洞察报告生成", 300L),

  /** RAG 文档摄入 */
  DOC_INGEST("DOC_INGEST", "文档摄入索引", 600L),

  /** 批量对话 */
  BATCH_CHAT("BATCH_CHAT", "批量对话处理", 120L),

  /** 代码执行 */
  CODE_EXECUTION("CODE_EXECUTION", "代码沙箱执行", 60L);

  private final String code;

  private final String description;

  /** 默认超时时间（秒） */
  private final long defaultTimeoutSeconds;

  AsyncTaskType(String code, String description, long defaultTimeoutSeconds) {
    this.code = code;
    this.description = description;
    this.defaultTimeoutSeconds = defaultTimeoutSeconds;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public long getDefaultTimeoutSeconds() {
    return defaultTimeoutSeconds;
  }

  /**
   * 根据编码解析枚举。
   *
   * @param code 类型编码
   * @return 匹配枚举，未找到返回 null
   */
  public static AsyncTaskType fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (AsyncTaskType type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    return null;
  }
}
