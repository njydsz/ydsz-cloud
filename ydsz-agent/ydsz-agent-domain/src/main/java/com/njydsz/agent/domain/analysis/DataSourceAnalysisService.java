package com.njydsz.agent.domain.analysis;

/**
 * 数据分析服务领域网关接口。
 *
 * <p>根据数据源类型（SQL / Python / 混合）执行用户自然语言查询对应的数据分析，
 * 返回结构化的分析结果（摘要 + 原始数据 + 图表数据）。
 *
 * <p>内部协作：根据 {@link DataSourceAnalysisRequest#getDataSourceType()} 决定
 * 调用链路 —— "sql" 走 {@code Text2SQLService}，"python" 走 {@code CodeExecutionService}，
 * "mixed" 先 SQL 查询再 Python 分析。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface DataSourceAnalysisService {

  /**
   * 执行数据分析。
   *
   * <p>根据请求中的数据源类型路由到对应分析引擎，返回统一格式的分析结果。
   *
   * <p>数据源类型路由规则：
   * <ul>
   *   <li>sql — 调用 Text2SQLService 执行查询</li>
   *   <li>python — 调用 CodeExecutionService 执行 Python 分析脚本</li>
   *   <li>mixed — 先 SQL 查询再 Python 编排分析</li>
   * </ul>
   *
   * @param request 分析请求（含用户查询和数据源类型）
   * @return 结构化分析结果（摘要 + 数据 + 图表）
   * @throws DataSourceAnalysisException 当分析执行失败时抛出
   */
  DataSourceAnalysisResult analyze(DataSourceAnalysisRequest request);

  /**
   * 数据分析异常。
   */
  class DataSourceAnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final String errorCode;

    /**
     * 构造异常。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     */
    public DataSourceAnalysisException(String message, String errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    /**
     * 构造异常（携带根因）。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     * @param cause 根因异常
     */
    public DataSourceAnalysisException(String message, String errorCode, Throwable cause) {
      super(message, cause);
      this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
      return errorCode;
    }
  }
}
