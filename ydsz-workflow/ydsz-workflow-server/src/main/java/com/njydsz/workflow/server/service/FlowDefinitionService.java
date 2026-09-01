package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.vo.FlowBatchDeployResultVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDetailVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDiffVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVersionVO;
import com.njydsz.workflow.domain.vo.FlowMigrationImpactVO;
import com.njydsz.workflow.domain.vo.FlowRollbackResultVO;

/**
 * 流程定义 Service
 *
 * <p>提供流程部署、发布、停用、查询、设计器协同编辑、版本对比与回滚等能力，是工作流引擎的入口服务。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>定义管理</b>：部署（{@link #deploy}）、发布（{@link #publish}）、停用（{@link #deprecate}）、启用/停用切换
 *   <li><b>版本管理</b>：多版本并存、灰度发布、版本对比、版本切换（{@link #switchActiveVersion}）、一键回滚（{@link
 *       #rollbackDefinition}）
 *   <li><b>设计器集成</b>：设计器数据加载/保存、节点坐标更新、协同编辑锁（{@link #lockDefinition}）
 *   <li><b>配置管理</b>：节点表单字段权限（{@link #saveFormConfig}）、节点 SLA 配置（{@link #saveSlaConfig}）
 *   <li><b>变更分析</b>：迁移影响分析（{@link #analyzeMigrationImpact}）、版本差异对比（{@link #diffVersions}）
 *   <li><b>导入导出</b>：BPMN 2.0 zip 批量导入、JSON 单个导入/导出
 * </ul>
 *
 * <p><b>事务边界：</b>所有写操作（{@code deploy/publish/deprecate/...}）在 Service 层开启
 * {@code @Transactional(rollbackFor = Exception.class)}，{@link #batchDeployFromZip} 内每个文件独立事务。
 *
 * <p><b>权限模型：</b>{@link #publish}/{@link #deprecate}/{@link #rollbackDefinition} 等高危操作要求 {@code
 * FLOW_ADMIN} 角色，由 Service 层 {@code FlowAdminGuard} 显式校验。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.server.service.impl.FlowDefinitionServiceImpl 实现类
 * @see com.njydsz.workflow.server.guard.FlowAdminGuard 流程管理员权限校验
 */
public interface FlowDefinitionService {

  /**
   * 部署流程（基于 JSON 模型）
   *
   * <p>设计器保存草稿或导入流程时调用。流程定义状态为「草稿」（未发布）， 不会立刻被流程引擎加载使用。发布后变为「激活」状态。
   *
   * @param dto 部署参数（flowCode / flowName / category / model JSON / nodes / skips）
   * @return 流程定义 ID
   */
  String deploy(FlowDeployProcessDTO dto);

  /**
   * 发布流程（默认不强制发布，HIGH 风险时阻断）。
   *
   * <p>等价于 {@link #publish(String, boolean) publish(definitionId, false)}。
   *
   * @param definitionId 流程定义 ID
   */
  void publish(String definitionId);

  /**
   * P1-4: 发布流程（带版本兼容性校验）。
   *
   * <p>发布前检测当前同 flowCode 是否有激活版本及其在途实例，并比对新旧版本节点编码差异：
   *
   * <ul>
   *   <li>无激活版本或无在途实例 → 直接发布（NONE 风险）
   *   <li>有在途实例但节点未删除 → 记录警告日志后发布（LOW/MEDIUM 风险）
   *   <li>有在途实例卡在已删除节点（HIGH 风险）：
   *       <ul>
   *         <li>{@code force=false} 且 {@code ydsz.flow.publish-block-on-high-risk=true}（默认）→ 抛
   *             SysException 阻断
   *         <li>{@code force=true} → 记录警告日志后强制发布（需管理员权限）
   *       </ul>
   * </ul>
   *
   * @param definitionId 流程定义 ID
   * @param force true=跳过 HIGH 风险阻断（强制发布）；false=按配置阻断
   */
  void publish(String definitionId, boolean force);

  /**
   * 停用（失效）流程定义
   *
   * <p>停用后流程定义无法再发起新流程，但<b>已发起的实例不受影响</b>（仍可继续推进）。 如需彻底下线，请使用 {@link #disable} 关闭定义。
   *
   * @param definitionId 流程定义 ID
   */
  void deprecate(String definitionId);

  /**
   * 查指定版本的已发布流程定义
   *
   * @param flowCode 流程编码
   * @param version 版本号（{@code null} 时取最新激活版本）
   * @param tenantId 租户 ID
   * @return 已发布的流程定义 VO，未发布或不存在时返回 null
   */
  FlowDefinitionVO getPublished(String flowCode, String version, String tenantId);

  /**
   * 按编码查询最新版本流程定义（不区分发布状态）
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 最新版本流程定义 VO
   */
  FlowDefinitionVO getLatestByCode(String flowCode, String tenantId);

  /**
   * 分页查询流程定义列表
   *
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @param category 分类过滤（可空）
   * @param flowCode 流程编码模糊过滤（可空）
   * @return 流程定义列表（按更新时间倒序）
   */
  List<FlowDefinitionVO> page(int pageNo, int pageSize, String category, String flowCode);

  /**
   * P2-21: 流程定义详情查询（含节点 + 跳转）
   *
   * @param definitionId 流程定义 ID
   * @return 流程定义详情 VO；定义不存在返回 null
   */
  FlowDefinitionDetailVO getDetail(String definitionId);

  /**
   * P2-27: 切换流程定义的激活版本 — 失效同 flowCode 其他已发布版本，激活目标版本
   *
   * @param flowCode 流程编码
   * @param definitionId 目标流程定义 ID
   * @param tenantId 租户 ID（可空，默认 "1"）
   */
  void switchActiveVersion(String flowCode, String definitionId, String tenantId);

  /**
   * P2-28: 启用流程定义（activityStatus = 1）
   *
   * @param definitionId 流程定义 ID
   */
  void enable(String definitionId);

  /**
   * P2-28: 停用流程定义（activityStatus = 0）
   *
   * @param definitionId 流程定义 ID
   */
  void disable(String definitionId);

  /**
   * P2-40: 更新节点坐标（供前端设计器保存布局）
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param coordinate 坐标 JSON 字符串（如 {"x":100,"y":200}）
   */
  void updateNodeCoordinate(String definitionId, String nodeCode, String coordinate);

  /**
   * P2-41: 编辑未发布的流程定义草稿（更新元数据 + 可选更新节点/跳转）
   *
   * @param definitionId 流程定义 ID
   * @param dto 部署参数（含更新后的元数据与节点/跳转）
   */
  void updateDefinition(String definitionId, FlowDeployProcessDTO dto);

  /**
   * GAP-V2-06: 导出流程定义为 JSON（含定义元数据 + 节点 + 跳转）
   *
   * @param definitionId 流程定义 ID
   * @return JSON 字符串，包含 definition / nodes / skips 三个部分
   */
  String exportDefinition(String definitionId);

  /**
   * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
   *
   * @param json 导出的 JSON 字符串
   * @param tenantId 租户 ID（可空，默认从上下文获取）
   * @return 新创建的流程定义 ID
   */
  String importDefinition(String json, String tenantId);

  /**
   * GAP-V2-01: 获取设计器数据 — 返回完整流程图（节点+边+坐标），供前端设计器加载
   *
   * @param definitionId 流程定义 ID
   * @return Map 包含 definition / nodes（含 coordinate）/ edges（含 condition）
   */
  Map<String, Object> getDesignerData(String definitionId);

  /**
   * GAP-V2-01: 批量保存设计器数据 — 一次性保存节点坐标 + 边 + 节点属性
   *
   * @param definitionId 流程定义 ID
   * @param designerData 设计器数据（nodes + edges + definition 元数据）
   */
  void saveDesignerData(String definitionId, Map<String, Object> designerData);

  /**
   * GAP-V2-02: 获取节点表单字段配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return formFieldsConfig JSON 字符串（如 {"fieldKey":"EDIT|READONLY|HIDDEN",...}）
   */
  String getFormConfig(String definitionId, String nodeCode);

  /**
   * GAP-V2-02: 保存节点表单字段配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param formFieldsConfig 字段权限 JSON 字符串
   */
  void saveFormConfig(String definitionId, String nodeCode, String formFieldsConfig);

  /**
   * P1-2: 获取节点 SLA 配置（JSON 字符串）
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return slaConfig JSON 字符串（如 {@code
   *     {"timeoutMinutes":120,"action":"REMIND","urgeIntervalMinutes":60,"maxUrges":3,"escalateUserId":1}}），
   *     未配置返回 null
   */
  String getSlaConfig(String definitionId, String nodeCode);

  /**
   * P1-2: 保存节点 SLA 配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param slaConfig SLA 配置 JSON 字符串
   */
  void saveSlaConfig(String definitionId, String nodeCode, String slaConfig);

  /**
   * 列出流程定义的所有历史版本
   *
   * @param definitionId 流程定义 ID（用于获取 flowCode）
   * @return 版本列表
   */
  List<FlowDefinitionVersionVO> listVersions(String definitionId);

  /**
   * 对比两个版本的节点和连线差异
   *
   * @param definitionId 流程定义 ID（用于获取 flowCode）
   * @param version1 版本号 1（整数）
   * @param version2 版本号 2（整数）
   * @return 版本差异 VO
   */
  FlowDefinitionDiffVO diffVersions(String definitionId, Integer version1, Integer version2);

  /**
   * GAP-P1-6: 从 BPMN 部署包 .zip 批量导入流程定义。
   *
   * <p>zip 部署能力。遍历 zip
   * 内的 {@code .bpmn} / {@code .bpmn20.xml} 文件，逐个解析并委托 {@link #deploy} 入库。 单个文件失败不影响其他文件（每个 deploy
   * 是独立事务）。
   *
   * @param zipBytes zip 文件字节数组
   * @param tenantId 租户 ID（可空，默认从 SecurityContext 获取）
   * @return 批量导入结果 VO
   */
  FlowBatchDeployResultVO batchDeployFromZip(byte[] zipBytes, String tenantId);

  /**
   * P2-4: 加锁流程定义（设计器协同编辑）。
   *
   * <p>流程设计器"编辑锁定"机制：
   *
   * <ul>
   *   <li>未锁定 → 加锁成功，返回 true
   *   <li>同一人持锁 → 续约（刷新 lockedAt），返回 true
   *   <li>他人持锁且未超时 → 抛 SysException
   *   <li>他人持锁但已超时 → 强制抢占，返回 true
   * </ul>
   *
   * @param definitionId 流程定义 ID
   * @param userId 当前操作用户 ID
   * @return true=加锁成功
   * @throws SysException 当锁被他人持有时
   */
  boolean lockDefinition(String definitionId, String userId);

  /**
   * P2-4: 解锁流程定义（设计器协同编辑）。
   *
   * <p>仅持锁人本人可解锁；他人持锁或未锁定时抛 SysException。
   *
   * @param definitionId 流程定义 ID
   * @param userId 当前操作用户 ID
   * @return true=解锁成功
   * @throws SysException 当非持锁人尝试解锁时
   */
  boolean unlockDefinition(String definitionId, String userId);

  /**
   * P2-4: 查询流程定义的锁定状态。
   *
   * @param definitionId 流程定义 ID
   * @return Map 包含：
   *     <ul>
   *       <li>{@code locked} (boolean) — 是否锁定中
   *       <li>{@code lockedBy} (String) — 当前持锁人 ID（未锁定返回 null）
   *       <li>{@code lockedAt} (LocalDateTime) — 加锁时间（未锁定返回 null）
   *       <li>{@code expired} (boolean) — 锁是否已超时（可被抢占）
   *     </ul>
   *     定义不存在返回 null。
   */
  Map<String, Object> getLockStatus(String definitionId);

  /**
   * P2-5: 变更影响分析报告 — 评估老版本定义升级到新版本对在途实例的影响。
   *
   * <p>"流程定义升级影响分析"：
   *
   * <ul>
   *   <li>对比两个版本的节点 / 跳转差异（复用 {@link #diffVersions}）
   *   <li>统计老版本在途实例数 + 按当前节点分组分布
   *   <li>识别被删除节点上的在途实例（HIGH 风险：会卡死）
   *   <li>识别节点类型/审批人变更（MEDIUM 风险）
   *   <li>输出整体风险等级（HIGH / MEDIUM / LOW / NONE）与迁移建议
   * </ul>
   *
   * @param oldDefinitionId 老版本流程定义 ID
   * @param newDefinitionId 新版本流程定义 ID
   * @return 迁移影响分析 VO
   */
  FlowMigrationImpactVO analyzeMigrationImpact(String oldDefinitionId, String newDefinitionId);

  /**
   * P0-2: 流程定义一键回滚
   *
   * <p>流程定义一键回滚能力。将指定 flowCode 的激活版本 从当前版本切换回上一个已发布版本，并自动迁移在途实例。
   *
   * <p>执行步骤：
   *
   * <ol>
   *   <li>查询当前激活版本（status=1）
   *   <li>查询上一个已发布版本（按 flow_version DESC 排除当前版本取第一条）
   *   <li>调用 {@link #analyzeMigrationImpact} 评估迁移影响
   *   <li>风险等级为 HIGH 时抛异常（需人工介入），否则继续
   *   <li>调用 {@link #switchActiveVersion} 切换激活版本到上一个版本
   *   <li>调用 FlowInstanceMigrationService 迁移在途实例（自动映射节点）
   *   <li>返回回滚结果报告
   * </ol>
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID（可空，默认从上下文获取）
   * @return 回滚结果 VO
   */
  FlowRollbackResultVO rollbackDefinition(String flowCode, String tenantId);
}
