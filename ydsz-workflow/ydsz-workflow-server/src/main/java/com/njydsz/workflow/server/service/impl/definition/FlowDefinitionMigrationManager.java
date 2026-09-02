package com.njydsz.workflow.server.service.impl.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowSkipRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;

/**
 * 流程定义迁移管理器
 *
 * <p>承担流程定义<b>导入导出、版本对比、变更影响分析</b>相关全部职责：
 * JSON 导入导出、版本差异对比（节点+跳转）、在途实例迁移影响分析。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>导出</b>：将定义 + 节点 + 跳转序列化为 JSON 字符串
 *   <li><b>导入</b>：解析 JSON 字符串后委托 {@link FlowDefinitionDeployManager#deploy} 创建草稿
 *   <li><b>版本对比</b>：对比两个版本的节点/跳转差异（added/removed/modified）
 *   <li><b>影响分析</b>：评估老版本升级到新版本对在途实例的影响（风险等级 + 迁移建议）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class FlowDefinitionMigrationManager {

  /** 流程定义仓储 */
  private final FlowDefinitionRepository definitionRepository;

  /** 流程节点仓储 */
  private final FlowNodeRepository nodeRepository;

  /** 节点跳转仓储 */
  private final FlowSkipRepository skipRepository;

  /** 流程实例仓储（变更影响分析） */
  private final FlowInstanceRepository instanceRepository;

  /** 流程定义元数据缓存 */
  private final FlowDefinitionCacheService flowDefinitionCacheService;

  /** 部署管理器（导入时委托部署） */
  private final FlowDefinitionDeployManager deployManager;

  /** 查询服务（导出时获取详情） */
  private final FlowDefinitionQueryService queryService;

  public FlowDefinitionMigrationManager(
      FlowDefinitionRepository definitionRepository,
      FlowNodeRepository nodeRepository,
      FlowSkipRepository skipRepository,
      FlowInstanceRepository instanceRepository,
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowDefinitionDeployManager deployManager,
      FlowDefinitionQueryService queryService) {
    this.definitionRepository = definitionRepository;
    this.nodeRepository = nodeRepository;
    this.skipRepository = skipRepository;
    this.instanceRepository = instanceRepository;
    this.flowDefinitionCacheService = flowDefinitionCacheService;
    this.deployManager = deployManager;
    this.queryService = queryService;
  }

  /**
   * 导出流程定义
   *
   * <p>将定义 + 节点 + 跳转序列化为 JSON 字符串。
   *
   * @param definitionId 流程定义 ID
   * @return 流程定义 JSON 字符串（含 {@code definition/nodes/skips} 三元组）
   * @throws SysException {@code NOT_FOUND} — 流程定义不存在
   */
  @Transactional(readOnly = true)
  public String exportDefinition(String definitionId) {
    Map<String, Object> detail = queryService.getDetail(definitionId);
    if (detail == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    return YdszJson.toJson(detail);
  }

  /**
   * 导入流程定义
   *
   * <p>解析 {@link #exportDefinition} 产出的 JSON 字符串，构造 {@link FlowDeployProcessDTO} 后
   * 委托 {@link FlowDefinitionDeployManager#deploy} 创建为草稿（{@code isPublish=0}）。
   *
   * @param json 流程定义 JSON 字符串
   * @param tenantId 目标租户 ID
   * @return 新创建的草稿定义 ID
   * @throws SysException {@code BAD_REQUEST} — JSON 缺失/解析失败/必要字段为空
   */
  @Transactional(rollbackFor = Exception.class)
  public String importDefinition(String json, String tenantId) {
    if (!StringUtils.hasText(json)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("导入 JSON 不能为空")
          .build();
    }
    Map<String, Object> root;
    try {
      root = YdszJson.parseMap(json);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("JSON 解析失败: " + e.getMessage())
          .build();
    }
    if (root == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("JSON 内容为空")
          .build();
    }

    Map<String, Object> defJson = MapUtils.safeCastMap(MapUtils.getMap(root, "definition"));
    if (defJson == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("JSON 缺少 definition 字段")
          .build();
    }
    String flowCode = MapUtils.getString(defJson, "flowCode");
    String flowName = MapUtils.getString(defJson, "flowName");
    if (!StringUtils.hasText(flowCode) || !StringUtils.hasText(flowName)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("definition 中 flowCode/flowName 不能为空")
          .build();
    }

    FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
    dto.setFlowCode(flowCode);
    dto.setFlowName(flowName);
    dto.setVersion(MapUtils.getString(defJson, "version"));
    dto.setCategory(MapUtils.getString(defJson, "category"));
    dto.setDescription(MapUtils.getString(defJson, "description"));
    dto.setFormPath(MapUtils.getString(defJson, "formPath"));
    dto.setTenantId(tenantId);
    dto.setProviderTraceId(MapUtils.getString(defJson, "providerTraceId"));

    List<?> nodesJson = MapUtils.getList(root, "nodes");
    if (nodesJson != null && !nodesJson.isEmpty()) {
      List<FlowDeployProcessDTO.FlowNodeDTO> nodes = new ArrayList<>(16);
      for (int i = 0; i < nodesJson.size(); i++) {
        Map<String, Object> n = MapUtils.getMapFromList(nodesJson, i);
        FlowDeployProcessDTO.FlowNodeDTO node = new FlowDeployProcessDTO.FlowNodeDTO();
        node.setNodeCode(MapUtils.getString(n, "nodeCode"));
        node.setNodeName(MapUtils.getString(n, "nodeName"));
        node.setNodeType(MapUtils.getInteger(n, "nodeType"));
        node.setPermissionFlag(MapUtils.getString(n, "permissionFlag"));
        node.setSkipAnyNode(MapUtils.getString(n, "skipAnyNode"));
        nodes.add(node);
      }
      dto.setNodes(nodes);
    }

    List<?> skipsJson = MapUtils.getList(root, "skips");
    if (skipsJson != null && !skipsJson.isEmpty()) {
      List<FlowDeployProcessDTO.FlowSkipDTO> skips = new ArrayList<>(16);
      for (int i = 0; i < skipsJson.size(); i++) {
        Map<String, Object> s = MapUtils.getMapFromList(skipsJson, i);
        FlowDeployProcessDTO.FlowSkipDTO skip = new FlowDeployProcessDTO.FlowSkipDTO();
        skip.setSkipName(MapUtils.getString(s, "skipName"));
        skip.setSkipType(MapUtils.getString(s, "skipType"));
        skip.setSkipCondition(MapUtils.getString(s, "skipCondition"));
        skip.setToNodeCode(MapUtils.getString(s, "nextNodeCode"));
        String ext = MapUtils.getString(s, "ext");
        if (StringUtils.hasText(ext)) {
          try {
            ObjectNode extNode = YdszJson.parseObject(ext);
            skip.setFromNodeCode(extNode != null ? extNode.getString("sourceRef") : null);
          } catch (Exception e) {
            log.warn(
                "[Flow] 导入跳转 ext 解析失败: skipName={} err={}",
                MapUtils.getString(s, "skipName"),
                e.getMessage());
          }
        }
        skips.add(skip);
      }
      dto.setSkips(skips);
    }

    String newDefinitionId = deployManager.deploy(dto);
    log.info(
        "[Flow] 导入流程定义成功: flowCode={} version={} newDefId={}",
        dto.getFlowCode(),
        dto.getVersion(),
        newDefinitionId);
    return newDefinitionId;
  }

  /**
   * 对比两个版本的差异
   *
   * <p>输出结构包含 addedNodes/removedNodes/modifiedNodes（节点级差异）和
   * addedSkips/removedSkips（跳转级差异）。
   *
   * @param definitionId 流程定义 ID（用于回溯 {@code flowCode}）
   * @param version1 版本号 1
   * @param version2 版本号 2
   * @return 差异 Map
   * @throws SysException {@code NOT_FOUND} — 定义或版本不存在
   */
  @Transactional(readOnly = true)
  public Map<String, Object> diffVersions(String definitionId, Integer version1, Integer version2) {
    FlowDefinitionVO baseDef = findDefinitionOrThrow(definitionId);
    FlowDefinitionVO defV1 = findVersionOrThrow(baseDef.getFlowCode(), version1);
    FlowDefinitionVO defV2 = findVersionOrThrow(baseDef.getFlowCode(), version2);

    VersionDiffContext ctx = loadVersionContext(defV1, defV2);
    Map<String, Object> nodeDiff = diffNodes(ctx.nodeMapV1, ctx.nodeMapV2);
    Map<String, Object> skipDiff = diffSkips(ctx.skipMapV1, ctx.skipMapV2);

    Map<String, Object> result = buildDiffResult(version1, version2, nodeDiff, skipDiff);
    logDiffResult(baseDef.getFlowCode(), version1, version2, nodeDiff, skipDiff);
    return result;
  }

  /**
   * 变更影响分析报告
   *
   * <p>评估老版本定义升级到新版本对在途实例的影响，输出版本差异、在途实例统计、
   * 受影响实例识别、风险等级与迁移建议。
   *
   * @param oldDefinitionId 老版本流程定义 ID
   * @param newDefinitionId 新版本流程定义 ID
   * @return 影响分析结果 Map
   */
  @Transactional(readOnly = true)
  public Map<String, Object> analyzeMigrationImpact(
      String oldDefinitionId, String newDefinitionId) {
    if (!StringUtils.hasText(oldDefinitionId) || !StringUtils.hasText(newDefinitionId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.migration.params.required")
          .build();
    }

    FlowDefinitionVO oldDef = definitionRepository.findById(oldDefinitionId).orElse(null);
    if (oldDef == null || (oldDef.getDeleted() != null && oldDef.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.migration.definition.not.found")
          .params(oldDefinitionId)
          .build();
    }
    FlowDefinitionVO newDef = definitionRepository.findById(newDefinitionId).orElse(null);
    if (newDef == null || (newDef.getDeleted() != null && newDef.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.migration.definition.not.found")
          .params(newDefinitionId)
          .build();
    }
    if (!Objects.equals(oldDef.getFlowCode(), newDef.getFlowCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.migration.flowcode.mismatch")
          .build();
    }

    Integer v1 = parseVersionInt(oldDef.getFlowVersion());
    Integer v2 = parseVersionInt(newDef.getFlowVersion());
    Map<String, Object> diff = diffVersions(oldDefinitionId, v1, v2);

    long runningTotal = instanceRepository.countRunningByDefinition(oldDefinitionId);
    List<Map<String, Object>> runningByNode = instanceRepository.selectRunningGroupByNode(oldDefinitionId);

    Map<String, Object> nodeChanges = MapUtils.safeCastMap(diff.get("nodeChanges"));
    List<Map<String, Object>> removedNodes = MapUtils.getListOfMaps(nodeChanges, "removed");
    Set<String> removedNodeCodes =
        removedNodes != null
            ? removedNodes.stream()
                .map(n -> String.valueOf(n.get("nodeCode")))
                .collect(Collectors.toSet())
            : Set.of();

    List<Map<String, Object>> modifiedNodes = MapUtils.getListOfMaps(nodeChanges, "modified");

    List<Map<String, Object>> stuckInstances = new ArrayList<>(16);
    List<Map<String, Object>> affectedInstances = new ArrayList<>(16);
    for (Map<String, Object> node : runningByNode) {
      String nodeCode = String.valueOf(node.get("currentNodeCode"));
      long cnt = ((Number) node.get("cnt")).longValue();
      Map<String, Object> entry = new LinkedHashMap<>(16);
      entry.put("nodeCode", nodeCode);
      entry.put("currentNodeName", node.get("currentNodeName"));
      entry.put("instanceCount", cnt);
      if (removedNodeCodes.contains(nodeCode)) {
        entry.put("reason", "NODE_REMOVED");
        stuckInstances.add(entry);
      } else if (modifiedNodes.stream()
          .anyMatch(m -> nodeCode.equals(String.valueOf(m.get("nodeCode"))))) {
        entry.put("reason", "NODE_MODIFIED");
        affectedInstances.add(entry);
      }
    }

    String riskLevel;
    if (runningTotal == 0) {
      riskLevel = "NONE";
    } else if (!stuckInstances.isEmpty()) {
      riskLevel = "HIGH";
    } else if (!affectedInstances.isEmpty() || runningTotal > 100) {
      riskLevel = "MEDIUM";
    } else {
      riskLevel = "LOW";
    }

    List<String> recommendations =
        buildRecommendations(
            riskLevel,
            runningTotal,
            stuckInstances,
            affectedInstances,
            removedNodes,
            modifiedNodes);

    Map<String, Object> oldDefInfo = new LinkedHashMap<>(16);
    oldDefInfo.put("id", oldDef.getId());
    oldDefInfo.put("flowCode", oldDef.getFlowCode());
    oldDefInfo.put("flowName", oldDef.getFlowName());
    oldDefInfo.put("flowVersion", oldDef.getFlowVersion());

    Map<String, Object> newDefInfo = new LinkedHashMap<>(16);
    newDefInfo.put("id", newDef.getId());
    newDefInfo.put("flowCode", newDef.getFlowCode());
    newDefInfo.put("flowName", newDef.getFlowName());
    newDefInfo.put("flowVersion", newDef.getFlowVersion());

    Map<String, Object> runningInstances = new LinkedHashMap<>(16);
    runningInstances.put("total", runningTotal);
    runningInstances.put("byNode", runningByNode);

    Map<String, Object> impactedInstances = new LinkedHashMap<>(16);
    impactedInstances.put("stuckInstances", stuckInstances);
    impactedInstances.put("affectedInstances", affectedInstances);

    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("oldDefinition", oldDefInfo);
    result.put("newDefinition", newDefInfo);
    result.put("diff", diff);
    result.put("runningInstances", runningInstances);
    result.put("impactedInstances", impactedInstances);
    result.put("riskLevel", riskLevel);
    result.put("recommendations", recommendations);

    log.info(
        "[Flow] 变更影响分析: oldDef={} newDef={} running={} stuck={} affected={} risk={}",
        oldDefinitionId,
        newDefinitionId,
        runningTotal,
        stuckInstances.size(),
        affectedInstances.size(),
        riskLevel);
    return result;
  }

  /**
   * 根据 definitionId 查询流程定义，不存在则抛出 NOT_FOUND。
   *
   * @param definitionId 流程定义 ID
   * @return 流程定义 VO；不存在时抛 {@code NOT_FOUND}
   */
  private FlowDefinitionVO findDefinitionOrThrow(String definitionId) {
    FlowDefinitionVO def = definitionRepository.findById(definitionId)
        .orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    return def;
  }

  /**
   * 根据 flowCode + 版本号查找定义版本，不存在则抛出 NOT_FOUND。
   *
   * @param flowCode 流程编码
   * @param version 目标版本号整数
   * @return 匹配的流程定义 VO；不存在时抛 {@code NOT_FOUND}
   */
  private FlowDefinitionVO findVersionOrThrow(String flowCode, Integer version) {
    String versionStr = String.valueOf(version);
    FlowDefinitionVO def = definitionRepository.findByFlowCode(flowCode).stream()
        .filter(d -> versionStr.equals(d.getFlowVersion()))
        .findFirst()
        .orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("版本 " + version + " 不存在: flowCode=" + flowCode)
          .build();
    }
    return def;
  }

  /** 版本差异上下文：持有两个版本的节点和连线映射。 */
  private static class VersionDiffContext {
    final Map<String, FlowNodeVO> nodeMapV1;
    final Map<String, FlowNodeVO> nodeMapV2;
    final Map<String, FlowSkipVO> skipMapV1;
    final Map<String, FlowSkipVO> skipMapV2;

    VersionDiffContext(Map<String, FlowNodeVO> nodeMapV1, Map<String, FlowNodeVO> nodeMapV2,
        Map<String, FlowSkipVO> skipMapV1, Map<String, FlowSkipVO> skipMapV2) {
      this.nodeMapV1 = nodeMapV1;
      this.nodeMapV2 = nodeMapV2;
      this.skipMapV1 = skipMapV1;
      this.skipMapV2 = skipMapV2;
    }
  }

  /**
   * 加载两个版本的节点和连线数据，返回差异上下文。
   *
   * @param defV1 待对比的老版本流程定义 VO
   * @param defV2 待对比的新版本流程定义 VO
   * @return 加载了两个版本的节点与连线映射的上下文对象
   */
  private VersionDiffContext loadVersionContext(FlowDefinitionVO defV1, FlowDefinitionVO defV2) {
    List<FlowNodeVO> nodesV1 = nodeRepository.findByDefinitionId(defV1.getId());
    List<FlowNodeVO> nodesV2 = nodeRepository.findByDefinitionId(defV2.getId());
    List<FlowSkipVO> skipsV1 = skipRepository.findByDefinitionId(defV1.getId());
    List<FlowSkipVO> skipsV2 = skipRepository.findByDefinitionId(defV2.getId());

    Map<String, FlowNodeVO> nodeMapV1 = nodesV1.stream()
        .collect(Collectors.toMap(FlowNodeVO::getNodeCode, n -> n, (a, b) -> a));
    Map<String, FlowNodeVO> nodeMapV2 = nodesV2.stream()
        .collect(Collectors.toMap(FlowNodeVO::getNodeCode, n -> n, (a, b) -> a));
    Map<String, FlowSkipVO> skipMapV1 = buildSkipKeyMap(skipsV1);
    Map<String, FlowSkipVO> skipMapV2 = buildSkipKeyMap(skipsV2);

    return new VersionDiffContext(nodeMapV1, nodeMapV2, skipMapV1, skipMapV2);
  }

  /** 计算节点差异，返回 {added, removed, modified} Map。 */
  private Map<String, Object> diffNodes(Map<String, FlowNodeVO> nodeMapV1,
      Map<String, FlowNodeVO> nodeMapV2) {
    List<Map<String, Object>> addedNodes = new ArrayList<>(16);
    for (Map.Entry<String, FlowNodeVO> entry : nodeMapV2.entrySet()) {
      if (!nodeMapV1.containsKey(entry.getKey())) {
        FlowNodeVO n = entry.getValue();
        addedNodes.add(Map.of("nodeCode", n.getNodeCode(),
            "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
      }
    }

    List<Map<String, Object>> removedNodes = new ArrayList<>(16);
    for (Map.Entry<String, FlowNodeVO> entry : nodeMapV1.entrySet()) {
      if (!nodeMapV2.containsKey(entry.getKey())) {
        FlowNodeVO n = entry.getValue();
        removedNodes.add(Map.of("nodeCode", n.getNodeCode(),
            "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
      }
    }

    List<Map<String, Object>> modifiedNodes = new ArrayList<>(16);
    for (Map.Entry<String, FlowNodeVO> entry : nodeMapV1.entrySet()) {
      String code = entry.getKey();
      if (nodeMapV2.containsKey(code)) {
        FlowNodeVO n1 = entry.getValue();
        FlowNodeVO n2 = nodeMapV2.get(code);
        Map<String, Map<String, Object>> changes = diffNodeFields(n1, n2);
        if (!changes.isEmpty()) {
          Map<String, Object> modEntry = new LinkedHashMap<>(16);
          modEntry.put("nodeCode", code);
          modEntry.put("changes", changes);
          modifiedNodes.add(modEntry);
        }
      }
    }

    Map<String, Object> nodeChanges = new LinkedHashMap<>(16);
    nodeChanges.put("added", addedNodes);
    nodeChanges.put("removed", removedNodes);
    nodeChanges.put("modified", modifiedNodes);
    return nodeChanges;
  }

  /** 逐字段比较两个 FlowNodeVO，返回变更 Map（空 Map 表示无变化）。 */
  private Map<String, Map<String, Object>> diffNodeFields(FlowNodeVO n1, FlowNodeVO n2) {
    Map<String, Map<String, Object>> changes = new LinkedHashMap<>(16);
    compareField(changes, "nodeName", n1.getNodeName(), n2.getNodeName());
    compareField(changes, "nodeType", String.valueOf(n1.getNodeType()), String.valueOf(n2.getNodeType()));
    compareField(changes, "permissionFlag", n1.getPermissionFlag(), n2.getPermissionFlag());
    compareField(changes, "formFieldsConfig", n1.getFormFieldsConfig(), n2.getFormFieldsConfig());
    compareField(changes, "ext", n1.getExt(), n2.getExt());
    compareField(changes, "skipAnyNode", n1.getSkipAnyNode(), n2.getSkipAnyNode());
    compareField(changes, "slaConfig", n1.getSlaConfigJson(), n2.getSlaConfigJson());
    return changes;
  }

  /**
   * 比较单字段，different 时放入 changes Map。
   *
   * @param changes 变更收集器（key=fieldName, value={old, new}）
   * @param fieldName 字段名（如 nodeName / nodeType）
   * @param oldVal 版本 1 字段值
   * @param newVal 版本 2 字段值
   */
  private void compareField(Map<String, Map<String, Object>> changes,
      String fieldName, String oldVal, String newVal) {
    if (!Objects.equals(oldVal, newVal)) {
      changes.put(fieldName, Map.of(
          "old", oldVal != null ? oldVal : "",
          "new", newVal != null ? newVal : ""));
    }
  }

  /** 计算连线差异，返回 {added, removed} Map。 */
  private Map<String, Object> diffSkips(Map<String, FlowSkipVO> skipMapV1,
      Map<String, FlowSkipVO> skipMapV2) {
    List<Map<String, Object>> addedSkips = new ArrayList<>(16);
    for (Map.Entry<String, FlowSkipVO> entry : skipMapV2.entrySet()) {
      if (!skipMapV1.containsKey(entry.getKey())) {
        addedSkips.add(skipToMap(entry.getValue()));
      }
    }

    List<Map<String, Object>> removedSkips = new ArrayList<>(16);
    for (Map.Entry<String, FlowSkipVO> entry : skipMapV1.entrySet()) {
      if (!skipMapV2.containsKey(entry.getKey())) {
        removedSkips.add(skipToMap(entry.getValue()));
      }
    }

    Map<String, Object> skipChanges = new LinkedHashMap<>(16);
    skipChanges.put("added", addedSkips);
    skipChanges.put("removed", removedSkips);
    return skipChanges;
  }

  /** 构建版本差异结果 Map（包含 summary）。 */
  private Map<String, Object> buildDiffResult(Integer version1, Integer version2,
      Map<String, Object> nodeDiff, Map<String, Object> skipDiff) {
    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("version1", version1);
    result.put("version2", version2);
    result.put("nodeChanges", nodeDiff);
    result.put("skipChanges", skipDiff);

    List<Map<String, Object>> addedNodes = MapUtils.getListOfMaps(nodeDiff, "added");
    List<Map<String, Object>> removedNodes = MapUtils.getListOfMaps(nodeDiff, "removed");
    List<Map<String, Object>> modifiedNodes = MapUtils.getListOfMaps(nodeDiff, "modified");
    List<Map<String, Object>> addedSkips = MapUtils.getListOfMaps(skipDiff, "added");
    List<Map<String, Object>> removedSkips = MapUtils.getListOfMaps(skipDiff, "removed");

    Map<String, Object> summary = new LinkedHashMap<>(16);
    summary.put("totalNodeChanges",
        addedNodes.size() + removedNodes.size() + modifiedNodes.size());
    summary.put("totalSkipChanges", addedSkips.size() + removedSkips.size());
    summary.put("hasBreakingChange",
        !removedNodes.isEmpty() || !removedSkips.isEmpty());
    result.put("summary", summary);
    return result;
  }

  /**
   * 输出版本差异对比日志。
   *
   * @param flowCode 流程编码（日志上下文）
   * @param version1 版本 1 编号
   * @param version2 版本 2 编号
   * @param nodeDiff 节点差异 Map（含 added / removed / modified）
   * @param skipDiff 连线差异 Map（含 added / removed）
   */
  private void logDiffResult(String flowCode, Integer version1, Integer version2,
      Map<String, Object> nodeDiff, Map<String, Object> skipDiff) {
    List<Map<String, Object>> addedNodes = MapUtils.getListOfMaps(nodeDiff, "added");
    List<Map<String, Object>> removedNodes = MapUtils.getListOfMaps(nodeDiff, "removed");
    List<Map<String, Object>> modifiedNodes = MapUtils.getListOfMaps(nodeDiff, "modified");
    List<Map<String, Object>> addedSkips = MapUtils.getListOfMaps(skipDiff, "added");
    List<Map<String, Object>> removedSkips = MapUtils.getListOfMaps(skipDiff, "removed");

    log.info(
        "[Flow] 版本差异对比: flowCode={} v1={} v2={} "
            + "nodeAdded={} nodeRemoved={} nodeModified={} "
            + "skipAdded={} skipRemoved={}",
        flowCode, version1, version2,
        addedNodes.size(), removedNodes.size(), modifiedNodes.size(),
        addedSkips.size(), removedSkips.size());
  }

  /** 构建连线 key 映射：sourceRef + "->" + nextNodeCode */
  private Map<String, FlowSkipVO> buildSkipKeyMap(List<FlowSkipVO> skips) {
    Map<String, FlowSkipVO> map = new LinkedHashMap<>(16);
    for (FlowSkipVO skip : skips) {
      String key = buildSkipKey(skip);
      if (key != null) {
        map.put(key, skip);
      }
    }
    return map;
  }

  /**
   * 从 ext JSON 中提取 sourceRef，拼接 key
   *
   * @param skip 连线 VO（含 ext 中的 sourceRef 与 nextNodeCode）
   * @return 连线唯一键（格式：sourceRef->nextNodeCode）；信息缺失时回退 skip.id
   */
  private String buildSkipKey(FlowSkipVO skip) {
    String sourceRef = null;
    if (StringUtils.hasText(skip.getExt())) {
      try {
        Map<String, Object> extJson = YdszJson.parseMap(skip.getExt());
        sourceRef = MapUtils.getString(extJson, "sourceRef");
      } catch (Exception ignored) {
        // ignore parse error
      }
    }
    if (sourceRef != null && skip.getNextNodeCode() != null) {
      return sourceRef + "->" + skip.getNextNodeCode();
    }
    return skip.getId() != null ? String.valueOf(skip.getId()) : null;
  }

  /** 将连线转为 Map 表示 */
  private Map<String, Object> skipToMap(FlowSkipVO skip) {
    String sourceRef = null;
    if (StringUtils.hasText(skip.getExt())) {
      try {
        Map<String, Object> extJson = YdszJson.parseMap(skip.getExt());
        sourceRef = MapUtils.getString(extJson, "sourceRef");
      } catch (Exception ignored) {
        // ignore
      }
    }
    Map<String, Object> map = new LinkedHashMap<>(16);
    map.put("sourceRef", sourceRef != null ? sourceRef : "");
    map.put("nextNodeCode", skip.getNextNodeCode() != null ? skip.getNextNodeCode() : "");
    map.put("skipName", skip.getSkipName() != null ? skip.getSkipName() : "");
    map.put("skipType", skip.getSkipType() != null ? skip.getSkipType() : "");
    map.put("skipCondition", skip.getSkipCondition() != null ? skip.getSkipCondition() : "");
    return map;
  }

  /**
   * 解析版本号字符串为整数
   *
   * @param versionStr 版本号字符串（如 "1.0" / "2" / null）
   * @return 整数版本号（小数部分截断）；为空 / 解析失败时返回 0
   */
  private Integer parseVersionInt(String versionStr) {
    if (!StringUtils.hasText(versionStr)) {
      return 0;
    }
    try {
      String main =
          versionStr.contains(".") ? versionStr.substring(0, versionStr.indexOf('.')) : versionStr;
      return Integer.parseInt(main.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 根据风险等级和影响范围生成迁移建议
   *
   * @param riskLevel 影响分析风险等级（NONE / LOW / MEDIUM / HIGH）
   * @param runningTotal 老版本在途实例总数
   * @param stuckInstances 卡死在已删除节点的实例子列表
   * @param affectedInstances 停留在已修改节点的实例子列表
   * @param removedNodes 已删除的节点变更项列表
   * @param modifiedNodes 已修改的节点变更项列表
   * @return 按风险等级组装的迁移建议字符串列表
   */
  private List<String> buildRecommendations(
      String riskLevel,
      long runningTotal,
      List<Map<String, Object>> stuckInstances,
      List<Map<String, Object>> affectedInstances,
      List<Map<String, Object>> removedNodes,
      List<Map<String, Object>> modifiedNodes) {
    List<String> recs = new ArrayList<>(16);

    switch (riskLevel) {
      case "NONE":
        recs.add("无在途实例，可直接发布新版本");
        recs.add("建议发布后停用老版本，避免新实例继续使用老版本");
        break;
      case "LOW":
        recs.add("存在 " + runningTotal + " 个在途实例，但节点未变更，风险较低");
        recs.add("建议：发布新版本 + 等待在途实例自然完成后停用老版本");
        recs.add("可选：通知发起人主动撤回后重新发起以使用新版本");
        break;
      case "MEDIUM":
        if (!affectedInstances.isEmpty()) {
          recs.add("存在 " + affectedInstances.size() + " 个节点的在途实例受影响（类型/审批人变更）");
          recs.add("建议：通知相关审批人确认变更影响，必要时手工干预");
        }
        if (runningTotal > 100) {
          recs.add("在途实例数量较多（" + runningTotal + "），建议分批次迁移");
        }
        recs.add("建议：发布新版本但保留老版本激活，待在途实例自然消化后再切换");
        break;
      case "HIGH":
        recs.add("【高危】存在 " + stuckInstances.size() + " 个节点的在途实例将卡死");
        for (Map<String, Object> stuck : stuckInstances) {
          recs.add(
              "  - 节点 "
                  + stuck.get("nodeCode")
                  + "（"
                  + stuck.get("currentNodeName")
                  + "）有 "
                  + stuck.get("instanceCount")
                  + " 个实例无法继续流转");
        }
        recs.add("建议：发布新版本前必须先处理在途实例：");
        recs.add("  1) 对卡死节点的实例手工强制流转到新版本对应节点");
        recs.add("  2) 或通知发起人撤回后重新发起");
        recs.add("  3) 或保留老版本激活直到所有在途实例完成");
        recs.add("禁止：直接停用老版本会导致在途实例永久卡死");
        break;
      default:
        recs.add("未知风险等级: " + riskLevel);
    }
    return recs;
  }
}
