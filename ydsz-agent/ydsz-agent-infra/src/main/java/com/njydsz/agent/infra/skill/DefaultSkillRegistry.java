package com.njydsz.agent.infra.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillRegistry;
import com.njydsz.agent.domain.skill.SkillRepository;
import com.njydsz.agent.domain.skill.SkillRuntime;

/**
 * Skill 注册中心的默认实现。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储 Skill 定义，线程安全。
 * 路由逻辑：根据 {@link SkillDescriptor#targetType()} 匹配对应的 {@link SkillRuntime} Bean，
 * 执行时将请求委托给匹配的运行时。
 *
 * <p>支持持久化层 {@link SkillRepository} 同步：注册时保存到仓储，
 * 卸载时从仓储移除。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class DefaultSkillRegistry implements SkillRegistry {

  /** 超时上限（毫秒），超出此值视为配置错误 */
  private static final long MAX_TIMEOUT_MS = 600_000L;

  /** Skill 注册存储 */
  private final Map<String, SkillDescriptor> registry = new ConcurrentHashMap<>();

  /** Skill 持久化仓储（可为 null，非必填） */
  private final SkillRepository skillRepository;

  /** 可用运行时列表 */
  private final List<SkillRuntime> runtimes;

  /**
   * 构造注册中心。
   *
   * @param skillRepository Skill 持久化仓储（可选）
   * @param runtimes        可用运行时 Bean 列表
   */
  public DefaultSkillRegistry(SkillRepository skillRepository, List<SkillRuntime> runtimes) {
    this.skillRepository = skillRepository;
    this.runtimes = runtimes != null ? new ArrayList<>(runtimes) : new ArrayList<>();
  }

  /**
   * 注册一个 Skill 定义。
   *
   * <p>写入内存注册中心，同时同步到持久化仓储（如有）。
   *
   * @param descriptor Skill 定义描述（不可为 null）
   */
  @Override
  public void register(SkillDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor 不能为 null");
    registry.put(descriptor.skillCode(), descriptor);
    if (skillRepository != null) {
      skillRepository.save(descriptor);
    }
    log.info("[SkillRegistry] 注册 Skill: {} (target={})",
        descriptor.skillCode(), descriptor.targetType());
  }

  /**
   * 注销 Skill。
   *
   * <p>从内存注册中心移除，同时从持久化仓储删除（如有）。
   *
   * @param skillCode Skill 编码
   */
  @Override
  public void unregister(String skillCode) {
    registry.remove(skillCode);
    if (skillRepository != null) {
      skillRepository.delete(skillCode);
    }
    log.info("[SkillRegistry] 注销 Skill: {}", skillCode);
  }

  /**
   * 查找 Skill 定义。
   *
   * @param skillCode Skill 编码
   * @return Skill 定义（未找到返回 null）
   */
  @Override
  public SkillDescriptor findByCode(String skillCode) {
    return registry.get(skillCode);
  }

  /**
   * 列出所有已注册 Skill。
   *
   * @return Skill 定义列表
   */
  @Override
  public List<SkillDescriptor> findAll() {
    return new ArrayList<>(registry.values());
  }

  /**
   * 获取适合指定执行目标的运行时。
   *
   * <p>优先查找 target 类型匹配的运行时；不可用时降级到任何可用运行时。
   *
   * @param target 执行目标类型
   * @return Skill 运行时
   */
  @Override
  public SkillRuntime getRuntime(SkillExecutionTarget target) {
    // 优先精确匹配
    for (SkillRuntime runtime : runtimes) {
      if (runtime.getTargetType() == target && isRuntimeAvailable(runtime)) {
        return runtime;
      }
    }
    // 降级：使用任意可用运行时
    for (SkillRuntime runtime : runtimes) {
      if (isRuntimeAvailable(runtime)) {
        log.warn("[SkillRegistry] 目标运行时 {} 不可用，降级到 {}",
            target, runtime.getTargetType());
        return runtime;
      }
    }
    throw new IllegalStateException("无可用的 Skill 运行时: " + target);
  }

  /**
   * 验证执行上下文的合法性。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>skillCode 对应的 Skill 必须已注册</li>
   *   <li>inputParams 必须包含 inputSchema 中声明的所有 required=true 的参数</li>
   * </ul>
   *
   * @param context 执行上下文
   * @return 验证通过返回原始上下文（不可变）
   * @throws SkillValidationException 验证失败
   */
  @Override
  public SkillExecutionContext validateContext(SkillExecutionContext context)
      throws SkillExecutionException {
    String skillCode = context.skillCode();
    SkillDescriptor descriptor = registry.get(skillCode);
    if (descriptor == null) {
      throw new SkillExecutionException(skillCode,
          "Skill 未注册: " + skillCode);
    }

    // 基础参数校验
    Map<String, Object> inputSchema = descriptor.inputSchema();
    if (inputSchema != null && !inputSchema.isEmpty()) {
      Map<String, Object> properties =
          inputSchema.get("properties") instanceof Map
              ? (Map<String, Object>) inputSchema.get("properties")
              : new HashMap<>();

      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String paramName = entry.getKey();
        Object paramDef = entry.getValue();
        if (paramDef instanceof Map) {
          // YDIZ-WARN-001 允许保留：Skill 注册器返回原始类型，调用方保证 subtype 关系
          @SuppressWarnings("unchecked")
          Map<String, Object> paramMap = (Map<String, Object>) paramDef;
          Object required = paramMap.get("required");
          if (Boolean.TRUE.equals(required)
              && !context.inputParams().containsKey(paramName)) {
            throw new SkillExecutionException(skillCode,
                "缺少必填参数: " + paramName);
          }
        }
      }
    }

    // 超时校验：上限 600 秒
    if (context.timeoutMs() > MAX_TIMEOUT_MS) {
      throw new SkillExecutionException(skillCode,
          "超时配置超出上限（600s）: " + context.timeoutMs() + "ms");
    }

    return context;
  }

  /**
   * 判断指定 Skill 是否已注册。
   *
   * @param skillCode Skill 编码
   * @return true=已注册
   */
  @Override
  public boolean contains(String skillCode) {
    return registry.containsKey(skillCode);
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 安全地检查运行时可用性。
   *
   * @param runtime 运行时
   * @return true=可用
   */
  private boolean isRuntimeAvailable(SkillRuntime runtime) {
    try {
      return runtime.isAvailable();
    } catch (Exception e) {
      log.debug("[SkillRegistry] 运行时可用性检查失败: {}", runtime.getTargetType());
      return false;
    }
  }
}
