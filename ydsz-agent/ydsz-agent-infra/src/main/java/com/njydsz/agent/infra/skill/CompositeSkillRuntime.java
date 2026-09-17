package com.njydsz.agent.infra.skill;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillRuntime;

/**
 * 组合路由器——SkillRuntime 的 Primary 实现。
 *
 * <p>根据 {@link SkillDescriptor#targetType()} 路由到对应的运行时实现：
 *
 * <ul>
 *   <li>LOCAL → {@link LocalSkillRuntime}</li>
 *   <li>SANDBOX → {@link DockerSandboxSkillRuntime}</li>
 * </ul>
 *
 * <p>作为 Spring 容器中 {@link SkillRuntime} 的默认 Bean，统一管理运行时实例的生命周期。
 * 运行时列表通过构造器注入，新增实现无需修改本类代码。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Primary
@Component
public class CompositeSkillRuntime implements SkillRuntime {

  /** 运行时列表（通过 Spring 注入所有 SkillRuntime 实现） */
  private final List<SkillRuntime> runtimes;

  /**
   * 构造组合路由器。
   *
   * @param runtimes 所有 SkillRuntime Bean 列表（含本 Bean 外的 Local/Docker 实现）
   */
  public CompositeSkillRuntime(List<SkillRuntime> runtimes) {
    // 过滤掉自身（避免递归调用）
    this.runtimes = new ArrayList<>();
    for (SkillRuntime runtime : runtimes) {
      if (!(runtime instanceof CompositeSkillRuntime)) {
        this.runtimes.add(runtime);
      }
    }
  }

  /**
   * 根据 Descriptor 的 targetType 选择对应运行时并执行。
   *
   * @param descriptor Skill 定义描述
   * @param context    执行上下文
   * @return 执行结果
   * @throws SkillExecutionException 未找到匹配运行时或执行异常
   */
  @Override
  public SkillExecutionResult execute(SkillDescriptor descriptor, SkillExecutionContext context)
      throws SkillExecutionException {
    SkillExecutionTarget target = descriptor.targetType();
    SkillRuntime resolved = resolveRuntime(target);
    log.info("[CompositeSkillRuntime] 路由 Skill: {} → {}", descriptor.skillCode(), target);
    return resolved.execute(descriptor, context);
  }

  /**
   * 列出所有运行时的可用 Skill（合并去重）。
   *
   * @return 可用 Skill 编码列表
   */
  @Override
  public List<String> listAvailableSkills() {
    List<String> allSkills = new ArrayList<>();
    for (SkillRuntime runtime : runtimes) {
      try {
        allSkills.addAll(runtime.listAvailableSkills());
      } catch (Exception e) {
        log.warn("[CompositeSkillRuntime] 获取可用 Skill 失败: {} - {}",
            runtime.getTargetType(), e.getMessage());
      }
    }
    return allSkills;
  }

  /**
   * 运行时环境可用——任一底层运行时可用的情况下返回 true。
   *
   * @return true=有可用运行时
   */
  @Override
  public boolean isAvailable() {
    for (SkillRuntime runtime : runtimes) {
      try {
        if (runtime.isAvailable()) {
          return true;
        }
      } catch (Exception e) {
        log.debug("[CompositeSkillRuntime] 运行时检查失败: {}", runtime.getTargetType());
      }
    }
    return false;
  }

  /**
   * 组合路由器不直接对应某个具体目标类型，默认返回 LOCAL。
   *
   * @return LOCAL
   */
  @Override
  public SkillExecutionTarget getTargetType() {
    return SkillExecutionTarget.LOCAL;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 根据执行目标解析对应的运行时实现。
   *
   * @param target 执行目标类型
   * @return 匹配的运行时
   * @throws SkillExecutionException 未找到匹配运行时
   */
  private SkillRuntime resolveRuntime(SkillExecutionTarget target)
      throws SkillExecutionException {
    for (SkillRuntime runtime : runtimes) {
      if (runtime.getTargetType() == target) {
        if (runtime.isAvailable()) {
          return runtime;
        }
        log.warn("[CompositeSkillRuntime] 运行时不可用: {}", target);
      }
    }
    // 兜底到任何可用的运行时
    for (SkillRuntime runtime : runtimes) {
      if (runtime.isAvailable()) {
        log.warn("[CompositeSkillRuntime] 目标运行时不可用，降级到: {}",
            runtime.getTargetType());
        return runtime;
      }
    }
    throw new SkillExecutionException("", "无可用的 Skill 运行时: " + target);
  }
}
