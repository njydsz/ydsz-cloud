package com.njydsz.agent.infra.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillRepository;

/**
 * 基于内存的 Skill 仓储实现。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储 {@link SkillDescriptor}，线程安全。
 * 适用于开发与测试环境；生产环境建议替换为基于数据库的实现。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Component
public class InMemorySkillRepository implements SkillRepository {

  /** Skill 存储（key=skillCode） */
  private final Map<String, SkillDescriptor> skillStore = new ConcurrentHashMap<>();

  /**
   * 根据编码查找 Skill。
   *
   * @param skillCode Skill 编码
   * @return Skill 定义（可能为空）
   */
  @Override
  public Optional<SkillDescriptor> findByCode(String skillCode) {
    return Optional.ofNullable(skillStore.get(skillCode));
  }

  /**
   * 列出所有已注册 Skill。
   *
   * @return Skill 定义列表
   */
  @Override
  public List<SkillDescriptor> findAll() {
    return new ArrayList<>(skillStore.values());
  }

  /**
   * 保存或更新 Skill 定义。
   *
   * @param descriptor Skill 定义（不可为 null）
   */
  @Override
  public void save(SkillDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor 不能为 null");
    skillStore.put(descriptor.skillCode(), descriptor);
  }

  /**
   * 删除 Skill 定义。
   *
   * @param skillCode Skill 编码
   */
  @Override
  public void delete(String skillCode) {
    skillStore.remove(skillCode);
  }

  /**
   * 判断指定 Skill 是否存在。
   *
   * @param skillCode Skill 编码
   * @return true=存在
   */
  @Override
  public boolean exists(String skillCode) {
    return skillStore.containsKey(skillCode);
  }
}
