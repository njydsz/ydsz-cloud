package com.njydsz.agent.domain.skill;

import java.util.List;

/**
 * Skill 运行时接口（网关层）
 *
 * <p>为不同运行环境提供一致的命令与文件接口。实现类需负责将 Skill 执行请求
 * 路由到本地进程或 Docker 沙箱环境。
 *
 * <h3>设计模型</h3>
 *
 * <pre>
 * SkillDescriptor → SkillRuntime → Execution Target (LOCAL / SANDBOX) → SkillExecutionResult
 * </pre>
 *
 * <p><b>线程安全</b>：实现通常为单例 Bean，须保证 execute 方法可并发调用，
 * 内部通过进程隔离确保每次调用的安全性。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface SkillRuntime {

  /**
   * 执行一个 Skill。
   *
   * @param descriptor Skill 定义描述
   * @param context    执行上下文
   * @return 执行结果（含 stdout/产物/指标）
   * @throws SkillExecutionException 执行超时、沙箱初始化失败等严重异常
   */
  SkillExecutionResult execute(SkillDescriptor descriptor, SkillExecutionContext context);

  /**
   * 列出当前运行时环境可用的 Skill 脚本目录。
   *
   * @return 可用 Skill 编码列表
   */
  List<String> listAvailableSkills();

  /**
   * 检查运行时环境是否可用。
   *
   * <p>SANDBOX 模式下检查 Docker 守护进程是否可达；
   * LOCAL 模式下检查 Python 解释器是否存在。
   *
   * @return true=可用
   */
  boolean isAvailable();

  /**
   * 获取运行时环境类型。
   *
   * @return 执行目标类型
   */
  SkillExecutionTarget getTargetType();
}
