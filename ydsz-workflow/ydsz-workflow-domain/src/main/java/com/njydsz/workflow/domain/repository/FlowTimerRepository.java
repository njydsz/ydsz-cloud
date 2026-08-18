package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowTimerVO;

/**
 * 定时器仓储接口（domain 层契约）。
 *
 * <p>定义定时器（ydsz_flow_timer）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作定时器聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowTimerVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / taskId / nodeCode 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowTimerRepository {

  /**
   * 保存定时器（新增）。
   *
   * @param vo 定时器 VO
   * @return 保存后的定时器 VO（含生成的 id 与审计字段）
   */
  FlowTimerVO save(FlowTimerVO vo);

  /**
   * 根据 ID 查询定时器。
   *
   * @param id 定时器 ID
   * @return 定时器 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTimerVO> findById(String id);

  /**
   * 根据任务 ID 查询定时器。
   *
   * @param taskId 任务 ID
   * @return 定时器 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTimerVO> findByTaskId(String taskId);

  /**
   * 根据实例 ID 查询定时器列表。
   *
   * @param instanceId 实例 ID
   * @return 定时器 VO 列表
   */
  List<FlowTimerVO> findByInstanceId(String instanceId);

  /**
   * 根据 ID 删除定时器。
   *
   * @param id 定时器 ID
   */
  void deleteById(String id);

  /**
   * 根据实例 ID 删除所有定时器。
   *
   * @param instanceId 实例 ID
   */
  void deleteByInstanceId(String instanceId);

  /**
   * 更新定时器。
   *
   * @param vo 定时器 VO（含 id）
   * @return 更新后的定时器 VO
   */
  FlowTimerVO update(FlowTimerVO vo);
}
