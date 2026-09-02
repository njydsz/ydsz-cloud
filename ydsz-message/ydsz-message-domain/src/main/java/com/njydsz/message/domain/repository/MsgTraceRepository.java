package com.njydsz.message.domain.repository;

import java.util.List;

import com.njydsz.message.domain.query.MsgTraceQuery;
import com.njydsz.message.domain.vo.MsgTraceVO;

/**
 * 消息轨迹仓储接口（domain 层契约）。
 *
 * <p>定义消息轨迹的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgTraceVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgTraceQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgTraceVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgTraceRepository {

  /**
   * 保存轨迹记录。
   *
   * @param vo 轨迹 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgTraceVO vo);

  /**
   * 按条件查询轨迹列表。
   *
   * @param query 查询参数
   * @return 轨迹 VO 列表
   */
  List<MsgTraceVO> findList(MsgTraceQuery query);
}
