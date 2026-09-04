package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


/**
 * 历史实例仓储接口（domain 层契约）。
 *
 * <p>定义历史归档实例（ydsz_flow_his_instance）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作历史实例聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowHisInstanceVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（id / threshold / limit 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowHisInstanceRepository {

  /**
   * 保存历史实例（新增）。
   *
   * @param vo 历史实例 VO
   * @return 保存后的历史实例 VO（含生成的 id 与审计字段）
   */
  FlowHisInstanceVO save(FlowHisInstanceVO vo);

  /**
   * 根据 ID 查询历史实例。
   *
   * @param id 历史实例 ID
   * @return 历史实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowHisInstanceVO> findById(String id);

  /**
   * 查询指定时间之前归档的实例列表（清理用）。
   *
   * @param threshold 归档时间阈值
   * @param limit 返回数量上限
   * @return 历史实例 VO 列表
   */
  List<FlowHisInstanceVO> findArchivedBefore(LocalDateTime threshold, int limit);

  /**
   * 按 ID 列表批量删除历史实例。
   *
   * @param ids 实例 ID 列表
   * @return 删除行数
   */
  int deleteByIds(List<String> ids);
}
