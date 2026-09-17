package com.njydsz.agent.domain.skill;

import java.util.List;

/**
 * Skill 注册中心接口
 *
 * <p>管理 Skill 定义的注册、发现和运行时路由。桥接 Skill 定义描述与 SkillRuntime 执行。
 *
 * <p><b>线程安全</b>：注册中心为请求间共享单例，须保证 register/find 的并发安全。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface SkillRegistry {

  /**
   * 注册一个 Skill 定义。
   *
   * @param descriptor Skill 定义描述
   */
  void register(SkillDescriptor descriptor);

  /**
   * 注销 Skill。
   *
   * @param skillCode Skill 编码
   */
  void unregister(String skillCode);

  /**
   * 查找 Skill 定义。
   *
   * @param skillCode Skill 编码
   * @return Skill 定义（可能为空）
   */
  SkillDescriptor findByCode(String skillCode);

  /**
   * 列出所有已注册 Skill。
   *
   * @return Skill 定义列表
   */
  List<SkillDescriptor> findAll();

  /**
   * 获取适合指定执行目标的运行时。
   *
   * @param target 执行目标类型
   * @return Skill 运行时
   */
  SkillRuntime getRuntime(SkillExecutionTarget target);

  /**
   * 执行指定 Skill（自动路由到匹配的运行时）。
   *
   * @param context 执行上下文
   * @return 执行结果
   */
  SkillExecutionContext validateContext(SkillExecutionContext context);

  /**
   * 判断指定 Skill 是否已注册。
   *
   * @param skillCode Skill 编码
   * @return true=已注册
   */
  boolean contains(String skillCode);
}
