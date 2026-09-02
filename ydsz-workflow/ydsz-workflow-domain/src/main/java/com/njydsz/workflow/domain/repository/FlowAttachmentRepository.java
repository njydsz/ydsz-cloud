package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowAttachmentVO;

/**
 * 附件仓储接口（domain 层契约）。
 *
 * <p>定义附件（ydsz_flow_attachment）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作附件聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowAttachmentVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / taskId / businessId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowAttachmentRepository {

  /**
   * 保存附件（新增）。
   *
   * @param vo 附件 VO
   * @return 保存后的附件 VO（含生成的 id 与审计字段）
   */
  FlowAttachmentVO save(FlowAttachmentVO vo);

  /**
   * 根据 ID 查询附件。
   *
   * @param id 附件 ID
   * @return 附件 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAttachmentVO> findById(String id);

  /**
   * 根据实例 ID 查询附件列表。
   *
   * @param instanceId 实例 ID
   * @return 附件 VO 列表
   */
  List<FlowAttachmentVO> findByInstanceId(String instanceId);

  /**
   * 根据任务 ID 查询附件列表。
   *
   * @param taskId 任务 ID
   * @return 附件 VO 列表
   */
  List<FlowAttachmentVO> findByTaskId(String taskId);

  /**
   * 根据 ID 删除附件。
   *
   * @param id 附件 ID
   */
  void deleteById(String id);

  /**
   * 更新附件。
   *
   * @param vo 附件 VO（含 id）
   * @return 更新后的附件 VO
   */
  FlowAttachmentVO update(FlowAttachmentVO vo);
}
