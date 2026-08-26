package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgFeedbackQuery;
import com.njydsz.message.domain.vo.MsgFeedbackVO;

/**
 * 消息用户反馈仓储接口（domain 层契约）。
 *
 * <p>定义消息用户反馈的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgFeedbackVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgFeedbackQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgFeedbackVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgFeedbackRepository {

  /**
   * 保存反馈记录。
   *
   * @param vo 反馈 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgFeedbackVO vo);

  /**
   * 按条件查询反馈列表。
   *
   * @param query 查询参数
   * @return 反馈 VO 列表
   */
  List<MsgFeedbackVO> findList(MsgFeedbackQuery query);

  /**
   * 分页查询反馈。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgFeedbackVO>> findPage(MsgFeedbackQuery query);

  /**
   * 根据条件查询单条反馈记录。
   *
   * @param query 查询参数
   * @return 反馈 VO，未找到返回 {@code Optional.empty()}
   */
  Optional<MsgFeedbackVO> findOne(MsgFeedbackQuery query);
}
