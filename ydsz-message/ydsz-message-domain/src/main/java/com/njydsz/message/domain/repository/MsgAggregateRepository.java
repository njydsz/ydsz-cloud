package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgAggregateQuery;
import com.njydsz.message.domain.vo.MsgAggregateVO;

/**
 * 聚合批次仓储接口（domain 层契约）。
 *
 * <p>定义聚合批次的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgAggregateVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MsgAggregateQuery}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgAggregateVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgAggregateRepository {

  /**
   * 保存聚合批次（插入或更新）。
   *
   * @param vo 聚合批次 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgAggregateVO vo);

  /**
   * 根据主键查询聚合批次。
   *
   * @param id 聚合批次 ID
   * @return 聚合批次 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgAggregateVO> findById(String id);

  /**
   * 更新聚合批次。
   *
   * @param vo 聚合批次 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgAggregateVO vo);

  /**
   * 按条件查询单条聚合批次。
   *
   * @param query 查询参数
   * @return 聚合批次 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgAggregateVO> findOne(MsgAggregateQuery query);

  /**
   * 按条件查询聚合批次列表。
   *
   * @param query 查询参数
   * @return 聚合批次 VO 列表
   */
  List<MsgAggregateVO> findList(MsgAggregateQuery query);

  /**
   * 分页查询聚合批次。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgAggregateVO>> findPage(MsgAggregateQuery query);

  /**
   * CAS 更新单条聚合批次状态（占有式更新，防止并发冲突）。
   *
   * @param id 批次 ID
   * @param fromStatus 当前状态（期望值）
   * @param toStatus 目标状态
   * @return 影响行数（0 表示已被其他实例占有）
   */
  int updateStatus(String id, String fromStatus, String toStatus);

  /**
   * 按聚合组和接收人批量 CAS 更新状态。
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @param fromStatus 当前状态（期望值）
   * @param toStatus 目标状态
   * @return 影响行数
   */
  int updateStatusByGroup(String group, String receiver, String fromStatus, String toStatus);
}
