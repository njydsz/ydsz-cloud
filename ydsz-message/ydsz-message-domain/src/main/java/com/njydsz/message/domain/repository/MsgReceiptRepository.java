package com.njydsz.message.domain.repository;

import java.util.List;

import com.njydsz.message.domain.query.MsgReceiptQuery;
import com.njydsz.message.domain.vo.MsgReceiptVO;

/**
 * 消息回执仓储接口（domain 层契约）。
 *
 * <p>定义消息回执的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgReceiptVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgReceiptQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgReceiptVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgReceiptRepository {

  /**
   * 保存回执记录。
   *
   * @param vo 回执 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgReceiptVO vo);

  /**
   * 按条件查询回执列表。
   *
   * @param query 查询参数
   * @return 回执 VO 列表
   */
  List<MsgReceiptVO> findList(MsgReceiptQuery query);
}
