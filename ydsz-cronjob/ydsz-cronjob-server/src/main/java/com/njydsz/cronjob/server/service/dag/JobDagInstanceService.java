package com.njydsz.cronjob.server.service.dag;

import java.util.List;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

/**
 * DAG 工作流实例 Service
 *
 * <p>管理 DAG 实例(每次 DAG 触发生成一个实例)和节点实例(每个 DAG 节点的一次执行记录)的 查询、状态流转、上下文管理。是 DAG 监控和可观测的数据源。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>实例查询</b>：{@link #getInstanceById} / {@link #listByDagId} / {@link #listByStatus} /
 *       {@link #listNodes}
 *   <li><b>状态控制</b>：{@link #pauseInstance} / {@link #resumeInstance} / {@link #cancelInstance}
 *   <li><b>上下文</b>：{@link #updateContext} — 跨节点传参(JSON 格式)
 *   <li><b>可视化(P4-1)</b>：{@link #getVisualization} — 返回 DAG 图 + 节点执行状态
 * </ul>
 *
 * <p><b>实例状态机：</b>{@code PENDING → RUNNING → COMPLETED / FAILED / CANCELED},或 {@code RUNNING →
 * PAUSED → RUNNING}。
 *
 * <p><b>可视化数据：</b>{@link #getVisualization} 返回包含 DAG 拓扑结构、各节点实时状态、 耗时、错误信息的 VO,前端可直接渲染为甘特图/流程图。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.vo.JobDagInstanceVO DAG 实例视图对象
 * @see com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO DAG 节点实例视图对象
 * @see JobDagService DAG 定义 Service
 */
public interface JobDagInstanceService {

  /**
   * 查询 DAG 实例详情。
   *
   * @param instanceId 实例 ID
   * @return DAG 实例 VO
   * @throws SysException 当实例不存在时抛出
   */
  JobDagInstanceVO getInstanceById(String instanceId);

  /**
   * 查询指定 DAG 的实例列表（按创建时间倒序）。
   *
   * @param dagId DAG 定义 ID
   * @param limit 最多返回条数
   * @return DAG 实例 VO 列表
   */
  List<JobDagInstanceVO> listByDagId(String dagId, int limit);

  /**
   * 按状态查询 DAG 实例。
   *
   * @param status 实例状态
   * @return DAG 实例 VO 列表
   */
  List<JobDagInstanceVO> listByStatus(String status);

  /**
   * 查询 DAG 实例的节点列表。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> listNodes(String dagInstanceId);

  /**
   * 暂停 DAG 实例（RUNNING → PAUSED）。
   *
   * @param instanceId 实例 ID
   * @throws SysException 当实例不存在或状态非 RUNNING 时抛出
   */
  void pauseInstance(String instanceId);

  /**
   * 恢复 DAG 实例（PAUSED → RUNNING）。
   *
   * @param instanceId 实例 ID
   * @throws SysException 当实例不存在或状态非 PAUSED 时抛出
   */
  void resumeInstance(String instanceId);

  /**
   * 取消 DAG 实例（RUNNING/PAUSED → CANCELED）。
   *
   * @param instanceId 实例 ID
   * @throws SysException 当实例不存在或已为终态时抛出
   */
  void cancelInstance(String instanceId);

  /**
   * 更新 DAG 实例上下文 JSON（跨节点传参用）。
   *
   * @param instanceId 实例 ID
   * @param contextJson 上下文 JSON
   * @throws SysException 当实例不存在时抛出
   */
  void updateContext(String instanceId, String contextJson);

  /**
   * P4-1: 获取 DAG 实例可视化数据（DAG 定义 + 节点执行状态）。
   *
   * @param instanceId 实例 ID
   * @return 可视化数据 VO
   * @throws SysException 当实例不存在或 DAG 定义非法时抛出
   */
  DagInstanceVisualizationVO getVisualization(String instanceId);

  /**
   * P2-2: 生成 DAG 实例的 Mermaid 时序图文本。
   *
   * <p>基于 DAG 定义和节点实时执行状态，生成 Mermaid {@code graph TD} 格式文本。 可直接粘贴到支持 Mermaid 的 Markdown
   * 编辑器（Typora/GitHub/Notion）中渲染。
   *
   * <p>节点颜色规则：
   *
   * <ul>
   *   <li>绿色（#4caf50）：执行成功（SUCCESS）
   *   <li>绿色（#4caf50）：执行成功（SUCCESS）
   *   <li>橙色（#ff9800）：执行中（RUNNING）
   *   <li>红色（#f44336）：执行失败（FAILED）
   *   <li>灰色（#9e9e9e）：待执行（PENDING）或已跳过（SKIPPED）
   * </ul>
   *
   * @param instanceId 实例 ID
   * @return Mermaid 图表文本；DAG 定义非法时返回注释说明
   * @throws SysException 当实例不存在时抛出
   */
  String getMermaidDiagram(String instanceId);
}
