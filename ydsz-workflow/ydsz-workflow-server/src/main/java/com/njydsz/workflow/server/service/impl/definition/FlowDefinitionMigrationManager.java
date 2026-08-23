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
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;
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
 * @since 1.0.0
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

  /** DO/VO 转换器 */
  private final WorkflowConverter converter;

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
      WorkflowConverter converter,
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowDefinitionDeployManager deployManager,
      FlowDefinitionQueryService queryService) {
    this.definitionRepository = definitionRepository;
    this.nodeRepository = nodeRepository;
    this.skipRepository = skipRepository;
    this.instanceRepository = instanceRepository;
    this.converter = converter;
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
      List<FlowDeployProcessDTO.FlowNodeDTO> nodes = new ArrayList<>();
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
      List<FlowDeployProcessDTO.FlowSkipDTO> skips = new ArrayList<>();
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
    FlowDefinitionDO baseDef = findDefinitionOrThrow(definitionId);
    FlowDefinitionDO defV1 = findVersionOrThrow(baseDef.getFlowCode(), version1);
    FlowDefinitionDO defV2 = findVersionOrThrow(baseDef.getFlowCode(), version2);

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
          .message("error.workflow.msg_c2d3e4f5")
          .build();
    }

    FlowDefinitionDO oldDef = definitionRepository.findById(oldDefinitionId).map(converter::entityToDO).orElse(null);
    if (oldDef == null || (oldDef.getDeleted() != null && oldDef.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_e7f8a9b0")
          .params(oldDefinitionId)
          .build();
    }
    FlowDefinitionDO newDef = definitionRepository.findById(newDefinitionId).map(converter::entityToDO).orElse(null);
    if (newDef == null || (newDef.getDeleted() != null && newDef.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_e7f8a9b0")
          .params(newDefinitionId)
          .build();
    }
    if (!Objects.equals(oldDef.getFlowCode(), newDef.getFlowCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_d3e4f5a6")
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

    List<Map<String, Object>> stuckInstances = new ArrayList<>();
    List<Map<String, Object>> affectedInstances = new ArrayList<>();
    for (Map<String, Object> node : runningByNode) {
      String nodeCode = String.valueOf(node.get("currentNodeCode"));
      long cnt = ((Number) node.get("cnt")).longValue();
      Map<String, Object> entry = new LinkedHashMap<>();
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

    Map<String, Object> oldDefInfo = new LinkedHashMap<>();
    oldDefInfo.put("id", oldDef.getId());
    oldDefInfo.put("flowCode", oldDef.getFlowCode());
    oldDefInfo.put("flowName", oldDef.getFlowName());
    oldDefInfo.put("flowVersion", oldDef.getFlowVersion());

    Map<String, Object> newDefInfo = new LinkedHashMap<>();
    newDefInfo.put("id", newDef.getId());
    newDefInfo.put("flowCode", newDef.getFlowCode());
    newDefInfo.put("flowName", newDef.getFlowName());
    newDefInfo.put("flowVersion", newDef.getFlowVersion());

    Map<String, Object> runningInstances = new LinkedHashMap<>();
    runningInstances.put("total", runningTotal);
    runningInstances.put("byNode", runningByNode);

    Map<String, Object> impactedInstances = new LinkedHashMap<>();
    impactedInstances.put("stuckInstances", stuckInstances);
    impactedInstances.put("affectedInstances", affectedInstances);

    Map<String, Object> result = new LinkedHashMap<>();
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
   * @param definitionId 参数说明
   * @return 返回值说明
   */
  private FlowDefinitionDO findDefinitionOrThrow(String definitionId) {
    FlowDefinitionDO def = definitionRepository.findById(definitionId)
        .map(converter::entityToDO)
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
   * @param flowCode 参数说明
   * @param version 参数说明
   * @return 返回值说明
   */
  private FlowDefinitionDO findVersionOrThrow(String flowCode, Integer version) {
    String versionStr = String.valueOf(version);
    FlowDefinitionDO def = definitionRepository.findByFlowCode(flowCode).stream()
        .map(converter::entityToDO)
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
    final Map<String, FlowNodeDO> nodeMapV1;
    final Map<String, FlowNodeDO> nodeMapV2;
    final Map<String, FlowSkipDO> skipMapV1;
    final Map<String, FlowSkipDO> skipMapV2;

    VersionDiffContext(Map<String, FlowNodeDO> nodeMapV1, Map<String, FlowNodeDO> nodeMapV2,
        Map<String, FlowSkipDO> skipMapV1, Map<String, FlowSkipDO> skipMapV2) {
      this.nodeMapV1 = nodeMapV1;
      this.nodeMapV2 = nodeMapV2;
      this.skipMapV1 = skipMapV1;
      this.skipMapV2 = skipMapV2;
    }
  }

  /**
   * 加载两个版本的节点和连线数据，返回差异上下文。
   *
   * @param defV1 参数说明
   * @param defV2 参数说明
   * @return 返回值说明
   */
  private VersionDiffContext loadVersionContext(FlowDefinitionDO defV1, FlowDefinitionDO defV2) {
    List<FlowNodeDO> nodesV1 = nodeRepository.findByDefinitionId(defV1.getId()).stream()
        .map(converter::entityToDO).toList();
    List<FlowNodeDO> nodesV2 = nodeRepository.findByDefinitionId(defV2.getId()).stream()
        .map(converter::entityToDO).toList();
    List<FlowSkipDO> skipsV1 = skipRepository.findByDefinitionId(defV1.getId()).stream()
        .map(converter::entityToDO).toList();
    List<FlowSkipDO> skipsV2 = skipRepository.findByDefinitionId(defV2.getId()).stream()
        .map(converter::entityToDO).toList();

    Map<String, FlowNodeDO> nodeMapV1 = nodesV1.stream()
        .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));
    Map<String, FlowNodeDO> nodeMapV2 = nodesV2.stream()
        .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));
    Map<String, FlowSkipDO> skipMapV1 = buildSkipKeyMap(skipsV1);
    Map<String, FlowSkipDO> skipMapV2 = buildSkipKeyMap(skipsV2);

    return new VersionDiffContext(nodeMapV1, nodeMapV2, skipMapV1, skipMapV2);
  }

  /** 计算节点差异，返回 {added, removed, modified} Map。 */
  @SuppressWarnings("unchecked")
  private Map<String, Object> diffNodes(Map<String, FlowNodeDO> nodeMapV1,
      Map<String, FlowNodeDO> nodeMapV2) {
    List<Map<String, Object>> addedNodes = new ArrayList<>();
    for (Map.Entry<String, FlowNodeDO> entry : nodeMapV2.entrySet()) {
      if (!nodeMapV1.containsKey(entry.getKey())) {
        FlowNodeDO n = entry.getValue();
        addedNodes.add(Map.of("nodeCode", n.getNodeCode(),
            "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
      }
    }

    List<Map<String, Object>> removedNodes = new ArrayList<>();
    for (Map.Entry<String, FlowNodeDO> entry : nodeMapV1.entrySet()) {
      if (!nodeMapV2.containsKey(entry.getKey())) {
        FlowNodeDO n = entry.getValue();
        removedNodes.add(Map.of("nodeCode", n.getNodeCode(),
            "nodeName", n.getNodeName() != null ? n.getNodeName() : ""));
      }
    }

    List<Map<String, Object>> modifiedNodes = new ArrayList<>();
    for (Map.Entry<String, FlowNodeDO> entry : nodeMapV1.entrySet()) {
      String code = entry.getKey();
      if (nodeMapV2.containsKey(code)) {
        FlowNodeDO n1 = entry.getValue();
        FlowNodeDO n2 = nodeMapV2.get(code);
        Map<String, Map<String, Object>> changes = diffNodeFields(n1, n2);
        if (!changes.isEmpty()) {
          Map<String, Object> modEntry = new LinkedHashMap<>();
          modEntry.put("nodeCode", code);
          modEntry.put("changes", changes);
          modifiedNodes.add(modEntry);
        }
      }
    }

    Map<String, Object> nodeChanges = new LinkedHashMap<>();
    nodeChanges.put("added", addedNodes);
    nodeChanges.put("removed", removedNodes);
    nodeChanges.put("modified", modifiedNodes);
    return nodeChanges;
  }

  /** 逐字段比较两个 FlowNodeDO，返回变更 Map（空 Map 表示无变化）。 */
  private Map<String, Map<String, Object>> diffNodeFields(FlowNodeDO n1, FlowNodeDO n2) {
    Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
    compareField(changes, "nodeName", n1.getNodeName(), n2.getNodeName());
    compareField(changes, "nodeType", n1.getNodeType(), n2.getNodeType());
    compareField(changes, "permissionFlag", n1.getPermissionFlag(), n2.getPermissionFlag());
    compareField(changes, "formFieldsConfig", n1.getFormFieldsConfig(), n2.getFormFieldsConfig());
    compareField(changes, "ext", n1.getExt(), n2.getExt());
    compareField(changes, "skipAnyNode", n1.getSkipAnyNode(), n2.getSkipAnyNode());
    compareField(changes, "slaConfig", n1.getSlaConfig(), n2.getSlaConfig());
    return changes;
  }

  /**
   * 比较单字段，different 时放入 changes Map。
   *
   * @param changes 参数说明
   * @param fieldName 参数说明
   * @param oldVal 参数说明
   * @param newVal 参数说明
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
  @SuppressWarnings("unchecked")
  private Map<String, Object> diffSkips(Map<String, FlowSkipDO> skipMapV1,
      Map<String, FlowSkipDO> skipMapV2) {
    List<Map<String, Object>> addedSkips = new ArrayList<>();
    for (Map.Entry<String, FlowSkipDO> entry : skipMapV2.entrySet()) {
      if (!skipMapV1.containsKey(entry.getKey())) {
        addedSkips.add(skipToMap(entry.getValue()));
      }
    }

    List<Map<String, Object>> removedSkips = new ArrayList<>();
    for (Map.Entry<String, FlowSkipDO> entry : skipMapV1.entrySet()) {
      if (!skipMapV2.containsKey(entry.getKey())) {
        removedSkips.add(skipToMap(entry.getValue()));
      }
    }

    Map<String, Object> skipChanges = new LinkedHashMap<>();
    skipChanges.put("added", addedSkips);
    skipChanges.put("removed", removedSkips);
    return skipChanges;
  }

  /** 构建版本差异结果 Map（包含 summary）。 */
  @SuppressWarnings("unchecked")
  private Map<String, Object> buildDiffResult(Integer version1, Integer version2,
      Map<String, Object> nodeDiff, Map<String, Object> skipDiff) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("version1", version1);
    result.put("version2", version2);
    result.put("nodeChanges", nodeDiff);
    result.put("skipChanges", skipDiff);

    List<Map<String, Object>> addedNodes = (List<Map<String, Object>>) nodeDiff.get("added");
    List<Map<String, Object>> removedNodes = (List<Map<String, Object>>) nodeDiff.get("removed");
    List<Map<String, Object>> modifiedNodes = (List<Map<String, Object>>) nodeDiff.get("modified");
    List<Map<String, Object>> addedSkips = (List<Map<String, Object>>) skipDiff.get("added");
    List<Map<String, Object>> removedSkips = (List<Map<String, Object>>) skipDiff.get("removed");

    Map<String, Object> summary = new LinkedHashMap<>();
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
   * @param flowCode 参数说明
   * @param version1 参数说明
   * @param version2 参数说明
   * @param nodeDiff 参数说明
   * @param skipDiff 参数说明
   */
  @SuppressWarnings("unchecked")
  private void logDiffResult(String flowCode, Integer version1, Integer version2,
      Map<String, Object> nodeDiff, Map<String, Object> skipDiff) {
    List<Map<String, Object>> addedNodes = (List<Map<String, Object>>) nodeDiff.get("added");
    List<Map<String, Object>> removedNodes = (List<Map<String, Object>>) nodeDiff.get("removed");
    List<Map<String, Object>> modifiedNodes = (List<Map<String, Object>>) nodeDiff.get("modified");
    List<Map<String, Object>> addedSkips = (List<Map<String, Object>>) skipDiff.get("added");
    List<Map<String, Object>> removedSkips = (List<Map<String, Object>>) skipDiff.get("removed");

    log.info(
        "[Flow] 版本差异对比: flowCode={} v1={} v2={} "
            + "nodeAdded={} nodeRemoved={} nodeModified={} "
            + "skipAdded={} skipRemoved={}",
        flowCode, version1, version2,
        addedNodes.size(), removedNodes.size(), modifiedNodes.size(),
        addedSkips.size(), removedSkips.size());
  }

  /** 构建连线 key 映射：sourceRef + "->" + nextNodeCode */
  private Map<String, FlowSkipDO> buildSkipKeyMap(List<FlowSkipDO> skips) {
    Map<String, FlowSkipDO> map = new LinkedHashMap<>();
    for (FlowSkipDO skip : skips) {
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
   * @param skip 参数说明
   * @return 返回值说明
   */
  private String buildSkipKey(FlowSkipDO skip) {
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
  private Map<String, Object> skipToMap(FlowSkipDO skip) {
    String sourceRef = null;
    if (StringUtils.hasText(skip.getExt())) {
      try {
        Map<String, Object> extJson = YdszJson.parseMap(skip.getExt());
        sourceRef = MapUtils.getString(extJson, "sourceRef");
      } catch (Exception ignored) {
        // ignore
      }
    }
    Map<String, Object> map = new LinkedHashMap<>();
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
   * @param versionStr 参数说明
   * @return 返回值说明
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
   * @param riskLevel 参数说明
   * @param runningTotal 参数说明
   * @param stuckInstances 参数说明
   * @param affectedInstances 参数说明
   * @param removedNodes 参数说明
   * @param modifiedNodes 参数说明
   * @return 返回值说明
   */
  private List<String> buildRecommendations(
      String riskLevel,
      long runningTotal,
      List<Map<String, Object>> stuckInstances,
      List<Map<String, Object>> affectedInstances,
      List<Map<String, Object>> removedNodes,
      List<Map<String, Object>> modifiedNodes) {
    List<String> recs = new ArrayList<>();

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
