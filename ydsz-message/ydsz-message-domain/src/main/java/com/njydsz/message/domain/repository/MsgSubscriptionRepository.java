package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgSubscriptionQuery;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;

/**
 * 消息订阅关系仓储接口（domain 层契约）。
 *
 * <p>定义消息订阅关系的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgSubscriptionVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgSubscriptionQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgSubscriptionVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgSubscriptionRepository {

  /**
   * 保存订阅关系（插入或更新）。
   *
   * @param vo 订阅关系 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgSubscriptionVO vo);

  /**
   * 更新订阅关系。
   *
   * @param vo 订阅关系 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgSubscriptionVO vo);

  /**
   * 按条件查询单条订阅关系。
   *
   * @param query 查询参数
   * @return 订阅关系 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgSubscriptionVO> findOne(MsgSubscriptionQuery query);

  /**
   * 按条件查询订阅关系列表。
   *
   * @param query 查询参数
   * @return 订阅关系 VO 列表
   */
  List<MsgSubscriptionVO> findList(MsgSubscriptionQuery query);

  /**
   * 按条件统计订阅关系数量。
   *
   * @param query 查询参数
   * @return 数量
   */
  long count(MsgSubscriptionQuery query);

  /**
   * 分页查询订阅关系。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgSubscriptionVO>> findPage(MsgSubscriptionQuery query);
}
