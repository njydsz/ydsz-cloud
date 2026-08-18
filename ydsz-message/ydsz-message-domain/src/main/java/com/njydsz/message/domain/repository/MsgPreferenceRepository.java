package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.message.domain.query.MsgPreferenceQuery;
import com.njydsz.message.domain.vo.MsgPreferenceVO;

/**
 * 用户消息偏好仓储接口（domain 层契约）。
 *
 * <p>定义用户消息偏好的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgPreferenceVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgPreferenceQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgPreferenceVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgPreferenceRepository {

  /**
   * 保存偏好记录（插入或更新）。
   *
   * @param vo 偏好 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgPreferenceVO vo);

  /**
   * 更新偏好记录。
   *
   * @param vo 偏好 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgPreferenceVO vo);

  /**
   * 根据主键删除偏好记录。
   *
   * @param id 偏好 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 按条件查询单条偏好记录。
   *
   * @param query 查询参数
   * @return 偏好 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgPreferenceVO> findOne(MsgPreferenceQuery query);

  /**
   * 按条件查询偏好列表。
   *
   * @param query 查询参数
   * @return 偏好 VO 列表
   */
  List<MsgPreferenceVO> findList(MsgPreferenceQuery query);
}
