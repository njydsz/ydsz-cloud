package com.njydsz.literule.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.literule.domain.dto.post.RuleVersionSaveDTO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;

/**
 * 规则版本仓库接口（DDD _domain 层）
 *
 * <p>定义规则版本持久化的标准操作，包括版本保存、查询、回滚等。 消费方可提供自定义实现（如数据库 + Redis 缓存）以满足不同性能与一致性需求。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RuleVersionRepository {

  /**
   * 保存规则版本快照
   *
   * <p>在规则创建/更新时调用，保存当前规则状态的完整快照， 用于后续回滚和历史追溯。
   *
   * @param saveDTO 规则版本保存 DTO（包含规则编码、版本号、定义 JSON、变更描述、操作人）
   */
  void saveVersion(RuleVersionSaveDTO saveDTO);

  /**
   * 查询规则的版本历史
   *
   * @param ruleCode 规则编码
   * @return 版本历史 VO 列表（按版本号降序）
   */
  List<RuleVersionVO> listVersions(String ruleCode);

  /**
   * 回滚到指定版本
   *
   * <p>加载目标版本的规则定义，恢复到规则表中，并保存一条新的版本记录（记录回滚操作）。
   *
   * @param ruleCode 规则编码
   * @param version 目标版本号
   * @param operator 操作人
   * @return 回滚后的规则定义 VO；目标版本不存在时返回 {@link Optional#empty()}
   */
  Optional<RuleDefinitionVO> rollback(String ruleCode, int version, String operator);
}
