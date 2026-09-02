package com.njydsz.literule.server.debug;

/**
 * 调试指令（F1 断点调试器）
 *
 * <p>调试客户端下发到 {@link DebugSession} 的控制指令：
 *
 * <ul>
 *   <li>{@link #RESUME} - 继续全速执行，后续断点命中仍会挂起
 *   <li>{@link #STEP_OVER} - 单步跳过（放行本次断点，执行到下一个断点/节点再挂起）
 *   <li>{@link #STEP_INTO} - 单步进入（对表达式节点级断点，进入子节点求值）
 *   <li>{@link #STEP_OUT} - 单步跳出（放行本次断点，继续到本规则评估结束或下一个断点）
 *   <li>{@link #TERMINATE} - 终止会话，后续断点不再挂起
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public enum DebugCommand {
  /** 继续全速执行 */
  RESUME,

  /** 单步跳过 */
  STEP_OVER,

  /** 单步进入 */
  STEP_INTO,

  /** 单步跳出 */
  STEP_OUT,

  /** 终止会话 */
  TERMINATE
}
