package com.njydsz.agent.domain.tool;

import java.util.Map;

/**
 * 工具执行器接口
 *
 * <p>每个工具对应一个 {@code ToolExecutor} 实现，接收参数 Map 并返回 JSON 字符串结果。
 *
 * <p><b>线程安全</b>：工具执行器可能被并发调用，实现须线程安全；execute 返回的 JSON 字符串建议为不可变结果，
 * 不要在实现内部持有跨调用共享的可变状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface ToolExecutor {

  /**
   * 执行工具
   *
   * @param arguments 工具参数（key=参数名, value=参数值）
   * @return 执行结果（JSON 字符串）
   * @throws ToolExecutionException 工具执行异常
   */
  String execute(Map<String, Object> arguments) throws ToolExecutionException;
}
