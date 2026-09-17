package com.njydsz.agent.domain.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 仓储接口
 *
 * <p>管理 Skill 定义的持久化和发现。实现可选择数据库、内存或本地文件系统。
 *
 * <p><b>DDD 合规</b>：接口定义在 domain 层，实现位于 infra 层。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface SkillRepository {

  /**
   * 根据编码查找 Skill。
   *
   * @param skillCode Skill 编码
   * @return Skill 定义（可能为空）
   */
  Optional<SkillDescriptor> findByCode(String skillCode);

  /**
   * 列出所有已注册 Skill。
   *
   * @return Skill 定义列表
   */
  List<SkillDescriptor> findAll();

  /**
   * 保存或更新 Skill 定义。
   *
   * @param descriptor Skill 定义
   */
  void save(SkillDescriptor descriptor);

  /**
   * 删除 Skill 定义。
   *
   * @param skillCode Skill 编码
   */
  void delete(String skillCode);

  /**
   * 判断指定 Skill 是否存在。
   *
   * @param skillCode Skill 编码
   * @return true=存在
   */
  boolean exists(String skillCode);
}
