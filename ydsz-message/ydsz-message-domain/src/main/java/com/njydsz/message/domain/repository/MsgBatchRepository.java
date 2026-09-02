package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.message.domain.query.MsgBatchQuery;
import com.njydsz.message.domain.vo.MsgBatchVO;

/**
 * 消息批次仓储接口（domain 层契约）。
 *
 * <p>定义消息批次的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgBatchVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgBatchQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgBatchVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgBatchRepository {

  /**
   * 保存消息批次（插入或更新）。
   *
   * @param vo 批次 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgBatchVO vo);

  /**
   * 根据主键查询消息批次。
   *
   * @param id 批次 ID
   * @return 批次 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgBatchVO> findById(String id);

  /**
   * 更新消息批次。
   *
   * @param vo 批次 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgBatchVO vo);

  /**
   * 按条件查询单条消息批次。
   *
   * @param query 查询参数
   * @return 批次 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgBatchVO> findOne(MsgBatchQuery query);

  /**
   * 按条件查询消息批次列表。
   *
   * @param query 查询参数
   * @return 批次 VO 列表
   */
  List<MsgBatchVO> findList(MsgBatchQuery query);
}
