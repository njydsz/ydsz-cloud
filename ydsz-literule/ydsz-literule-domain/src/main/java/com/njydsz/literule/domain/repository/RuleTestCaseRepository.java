package com.njydsz.literule.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.literule.domain.dto.post.RuleTestCasePostDTO;
import com.njydsz.literule.domain.vo.RuleTestCaseVO;

/**
 * 规则测试用例 Repository（domain 层契约）。
 *
 * <p>定义规则测试用例的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link RuleTestCaseVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RuleTestCaseRepository {

  /**
   * 查询测试用例列表（可选按规则编码过滤）。
   *
   * @param ruleCode 规则编码（可选，为空则查询全部）
   * @return 测试用例 VO 列表
   */
  List<RuleTestCaseVO> findByRuleCode(String ruleCode);

  /**
   * 查询全部测试用例。
   *
   * @return 测试用例 VO 列表
   */
  List<RuleTestCaseVO> findAll();

  /**
   * 根据 ID 查询测试用例。
   *
   * @param id 测试用例 ID
   * @return 测试用例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<RuleTestCaseVO> findById(Long id);

  /**
   * 保存测试用例（新增或更新）。
   *
   * <p>当 DTO 转换后的实体已包含 {@code id} 时执行更新，否则执行新增。
   *
   * @param dto 测试用例保存 DTO
   * @return 保存后的测试用例 VO
   */
  RuleTestCaseVO save(RuleTestCasePostDTO dto);

  /**
   * 根据 ID 删除测试用例。
   *
   * @param id 测试用例 ID
   */
  void deleteById(String id);
}
