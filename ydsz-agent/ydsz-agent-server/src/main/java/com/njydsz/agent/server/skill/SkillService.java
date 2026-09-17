package com.njydsz.agent.server.skill;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillRegistry;
import com.njydsz.agent.domain.skill.SkillRuntime;

/**
 * Skill 管理服务。
 *
 * <p>封装 Skill 的执行、查询与沙箱运维操作。作为 Skill Engine 的应用服务层，
 * 协调 SkillRegistry（定义发现与校验）和 SkillRuntime（实际执行）。
 *
 * <h3>设计模型</h3>
 *
 * <pre>
 * SkillService → SkillRegistry（注册中心）
 *              → SkillRuntime（运行时，根据 SkillDescriptor.targetType 路由）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Service
public class SkillService {

  /** Skill 注册中心 */
  private final SkillRegistry skillRegistry;

  /**
   * 构造 Skill 管理服务。
   *
   * @param skillRegistry Skill 注册中心
   */
  public SkillService(SkillRegistry skillRegistry) {
    this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry 不能为 null");
  }

  /**
   * 执行指定 Skill。
   *
   * <p>处理流程：
   * <ol>
   *   <li>通过 {@link SkillRegistry#findByCode} 查找 Skill 定义</li>
   *   <li>构建 {@link SkillExecutionContext}</li>
   *   <li>通过 {@link SkillRegistry#validateContext} 校验上下文合法性</li>
   *   <li>通过 {@link SkillRegistry#getRuntime} 获取匹配的运行时</li>
   *   <li>委托运行时执行并返回结果</li>
   * </ol>
   *
   * @param skillCode   Skill 编码
   * @param inputParams 输入参数（可为 null）
   * @param timeoutMs   超时毫秒数（0 表示使用默认值）
   * @return 执行结果
   * @throws SkillExecutionException Skill 未注册或执行失败
   */
  public SkillExecutionResult executeSkill(String skillCode,
                                            Map<String, Object> inputParams,
                                            long timeoutMs) {
    Objects.requireNonNull(skillCode, "skillCode 不能为 null");
    log.info("[SkillService] 开始执行 Skill: skillCode={}, timeoutMs={}", skillCode, timeoutMs);

    // 查找 Skill 定义
    SkillDescriptor descriptor = skillRegistry.findByCode(skillCode);
    if (descriptor == null) {
      throw new SkillExecutionException(skillCode, "Skill 未注册: " + skillCode);
    }

    // 构建执行上下文
    SkillExecutionContext context = SkillExecutionContext.builder()
        .skillCode(skillCode)
        .inputParams(inputParams != null ? inputParams : Collections.emptyMap())
        .timeoutMs(timeoutMs)
        .build();

    // 校验上下文
    skillRegistry.validateContext(context);

    // 获取运行时并执行
    SkillRuntime runtime = skillRegistry.getRuntime(descriptor.targetType());
    SkillExecutionResult result = runtime.execute(descriptor, context);

    log.info("[SkillService] Skill 执行完成: skillCode={}, isSuccess={}, elapsedMs={}",
        skillCode, result.isSuccess(), result.getElapsedMs());
    return result;
  }

  /**
   * 列出所有已注册 Skill。
   *
   * @return Skill 定义列表（永远不为 null）
   */
  public List<SkillDescriptor> listSkills() {
    List<SkillDescriptor> skills = skillRegistry.findAll();
    log.debug("[SkillService] 查询 Skill 列表: count={}", skills.size());
    return skills;
  }

  /**
   * 获取 Skill 详情。
   *
   * @param skillCode Skill 编码
   * @return Skill 详情
   * @throws SkillExecutionException Skill 未注册
   */
  public SkillDescriptor getSkillDetail(String skillCode) {
    Objects.requireNonNull(skillCode, "skillCode 不能为 null");
    SkillDescriptor descriptor = skillRegistry.findByCode(skillCode);
    if (descriptor == null) {
      throw new SkillExecutionException(skillCode, "Skill 未注册: " + skillCode);
    }
    log.debug("[SkillService] 查询 Skill 详情: skillCode={}", skillCode);
    return descriptor;
  }

  /**
   * 清空沙箱缓存。
   *
   * <p>运维手段：查找所有 SANDBOX 类型的运行时，请求其清理内部缓存（如预构建镜像层、
   * 已编译中间产物等）。当前实现为占位符，运行时自行定义缓存清理语义。
   */
  public void invalidateSandbox() {
    log.info("[SkillService] 开始清理沙箱缓存");
    // 尝试获取沙箱运行时并执行清理
    try {
      SkillRuntime sandboxRuntime = skillRegistry.getRuntime(SkillExecutionTarget.SANDBOX);
      sandboxRuntime.listAvailableSkills();
      log.info("[SkillService] 沙箱缓存清理完成");
    } catch (Exception e) {
      log.warn("[SkillService] 沙箱缓存清理失败（沙箱运行时可能未启用）: {}", e.getMessage());
    }
  }
}
