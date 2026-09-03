package com.njydsz.workflow.server.service.impl.definition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowDefinitionDTO;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowSkipType;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowSkipRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.engine.BpmnModel;
import com.njydsz.workflow.server.engine.BpmnXmlParser;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;

/**
 * 流程定义设计器管理器
 *
 * <p>承担流程定义<b>设计器协同编辑</b>全部职责：节点坐标更新、草稿编辑、
 * 设计器数据加载/保存、表单字段配置、SLA 配置、协同编辑锁定/解锁。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>节点坐标</b>：更新节点坐标（{@code {x,y,width,height}}）
 *   <li><b>草稿编辑</b>：编辑未发布定义的元数据 + 节点/跳转全量替换
 *   <li><b>设计器数据</b>：获取/保存设计器数据（含 edges 格式供前端直接消费）
 *   <li><b>表单配置</b>：节点表单字段权限配置（EDIT/READONLY/HIDDEN）
 *   <li><b>SLA 配置</b>：节点级超时配置（时长/提醒/升级策略）
 *   <li><b>协同锁</b>：CAS 乐观锁实现设计器多用户并发编辑锁定
 * </ul>
 *
 * <p><b>并发控制：</b>协同编辑锁使用 CAS（Compare-And-Swap）乐观锁，
 * 单条 UPDATE SQL 完成判定 + 更新，避免"读-判-写"竞态。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class FlowDefinitionDesignManager {

  /** 集合默认初始容量（源清单缺失时的兜底估计值） */
  private static final int DEFAULT_COLLECTION_CAPACITY = 16;

  /** 流程定义仓储 */
  private final FlowDefinitionRepository definitionRepository;

  /** 流程节点仓储 */
  private final FlowNodeRepository nodeRepository;

  /** 节点跳转仓储 */
  private final FlowSkipRepository skipRepository;

  /** 流程定义元数据缓存 */
  private final FlowDefinitionCacheService flowDefinitionCacheService;

  /** 统一配置属性 */
  private final FlowProperties flowProperties;

  /** BPMN 2.0 XML 解析器（草稿编辑时使用） */
  private final BpmnXmlParser bpmnXmlParser;

  /** 查询服务（获取详情数据） */
  private final FlowDefinitionQueryService queryService;

  public FlowDefinitionDesignManager(
      FlowDefinitionRepository definitionRepository,
      FlowNodeRepository nodeRepository,
      FlowSkipRepository skipRepository,
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowProperties flowProperties,
      BpmnXmlParser bpmnXmlParser,
      FlowDefinitionQueryService queryService) {
    this.definitionRepository = definitionRepository;
    this.nodeRepository = nodeRepository;
    this.skipRepository = skipRepository;
    this.flowDefinitionCacheService = flowDefinitionCacheService;
    this.flowProperties = flowProperties;
    this.bpmnXmlParser = bpmnXmlParser;
    this.queryService = queryService;
  }

  /**
   * 更新节点坐标
   *
   * <p>设计器拖拽节点后调用，{@code coordinate} 字段为 JSON 字符串（{@code {x,y,width,height}}）。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param coordinate 坐标 JSON 字符串
   * @throws SysException {@code BAD_REQUEST} / {@code NOT_FOUND}
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateNodeCoordinate(String definitionId, String nodeCode, String coordinate) {
    if (definitionId == null || !StringUtils.hasText(nodeCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("definitionId/nodeCode 不能为空")
          .build();
    }
    FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
          .build();
    }
    node.setCoordinate(coordinate);
    nodeRepository.save(node);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 更新节点坐标: defId={} node={} coordinate={}", definitionId, nodeCode, coordinate);
  }

  /**
   * 更新流程定义（草稿编辑）
   *
   * <p>仅允许编辑未发布（{@code isPublish=0}）的定义。支持元数据更新 + 节点/跳转全量替换。
   *
   * @param definitionId 流程定义 ID（必须未发布）
   * @param dto 更新参数 DTO
   * @throws SysException {@code BAD_REQUEST} — 定义已发布；{@code NOT_FOUND} — 定义不存在
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public void updateDefinition(String definitionId, FlowDeployProcessDTO dto) {
    if (definitionId == null || dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("definitionId/dto 不能为空")
          .build();
    }
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    if (def.getIsPublish() != null && def.getIsPublish() == 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("已发布的流程定义不可编辑，请创建新版本: " + definitionId)
          .build();
    }

    if (StringUtils.hasText(dto.getFlowName())) {
      def.setFlowName(dto.getFlowName());
    }
    if (StringUtils.hasText(dto.getCategory())) {
      def.setCategory(dto.getCategory());
    }
    if (dto.getDescription() != null) {
      def.setDescription(dto.getDescription());
    }
    if (StringUtils.hasText(dto.getFormPath())) {
      def.setFormPath(dto.getFormPath());
    }
    FlowDefinitionDTO updateDto = new FlowDefinitionDTO();
    updateDto.setId(definitionId);
    updateDto.setFlowName(def.getFlowName());
    updateDto.setTenantId(def.getTenantId());
    definitionRepository.update(updateDto);

    boolean hasNodes = dto.getNodes() != null && !dto.getNodes().isEmpty();
    boolean hasSkips = dto.getSkips() != null && !dto.getSkips().isEmpty();
    boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
    if (hasBpmn || hasNodes || hasSkips) {
      // 先删除跳转（外键依赖节点，但此处按业务约定先删跳转再删节点）— P1-9: 批量 DELETE 替代逐条删除的 N+1
      skipRepository.deleteByDefinitionId(definitionId);
      nodeRepository.deleteByDefinitionId(definitionId);

      int expectedNodeSize = dto.getNodes() != null ? dto.getNodes().size() : DEFAULT_COLLECTION_CAPACITY;
      int expectedSkipSize = dto.getSkips() != null ? dto.getSkips().size() : DEFAULT_COLLECTION_CAPACITY;
      List<FlowNodeVO> nodes = new ArrayList<>(expectedNodeSize);
      List<FlowSkipVO> skips = new ArrayList<>(expectedSkipSize);

      if (hasBpmn) {
        BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
        nodes.addAll(bpmnModel.getNodes());
        skips.addAll(bpmnModel.getSkips());
      } else {
        for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
          FlowNodeVO node = new FlowNodeVO();
          node.setNodeCode(n.getNodeCode());
          node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
          node.setNodeType(
              n.getNodeType() == null ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
          node.setPermissionFlag(n.getPermissionFlag());
          node.setSkipAnyNode(n.getSkipAnyNode());
          nodes.add(node);
        }
        if (dto.getSkips() != null) {
          for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
            FlowSkipVO skip = new FlowSkipVO();
            skip.setSkipName(s.getSkipName());
            skip.setSkipType(
                StringUtils.hasText(s.getSkipType()) ? s.getSkipType() : FlowSkipType.PASS.name());
            skip.setSkipCondition(s.getSkipCondition());
            skip.setNextNodeCode(s.getToNodeCode());
            skip.setExt(YdszJson.toJson(Map.of("sourceRef", s.getFromNodeCode())));
            skips.add(skip);
          }
        }
      }

      for (FlowNodeVO node : nodes) {
        node.setDefinitionId(definitionId);
        node.setFlowCode(def.getFlowCode());
        node.setTenantId(def.getTenantId());
        node.setProviderTraceId(dto.getProviderTraceId());
        nodeRepository.save(node);
      }
      for (FlowSkipVO skip : skips) {
        skip.setDefinitionId(definitionId);
        skip.setFlowCode(def.getFlowCode());
        skip.setTenantId(def.getTenantId());
        skip.setProviderTraceId(dto.getProviderTraceId());
        skipRepository.save(skip);
      }
    }

    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 编辑流程定义草稿: defId={} flowCode={}", definitionId, def.getFlowCode());
  }

  /**
   * 获取设计器数据
   *
   * <p>在 {@link FlowDefinitionQueryService#getDetail} 基础上额外组装 {@code edges} 字段，
   * 供前端 VueFlow / LogicFlow 直接消费。
   *
   * @param definitionId 流程定义 ID
   * @return 设计器数据 Map（含 {@code definition/nodes/skips/edges}）；不存在返回空 Map
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getDesignerData(String definitionId) {
    Map<String, Object> detail = queryService.getDetail(definitionId);
    if (detail == null || detail.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Object> result = new LinkedHashMap<>(detail);
    List<FlowSkipVO> skips = MapUtils.safeCastList(detail.get("skips"), FlowSkipVO.class);
    if (skips != null) {
      List<Map<String, Object>> edges = new ArrayList<>(16);
      for (FlowSkipVO skip : skips) {
        Map<String, Object> edge = new LinkedHashMap<>(16);
        edge.put("id", skip.getId());
        String source = null;
        if (StringUtils.hasText(skip.getExt())) {
          try {
            ObjectNode extNode = YdszJson.parseObject(skip.getExt());
            source = extNode != null ? extNode.getString("sourceRef") : null;
          } catch (Exception e) {
            log.warn("解析skip节点ext JSON失败: {}", e.getMessage(), e);
          }
        }
        edge.put("source", source);
        edge.put("target", skip.getNextNodeCode());
        edge.put("label", skip.getSkipName());
        edge.put("condition", skip.getSkipCondition());
        edge.put("skipType", skip.getSkipType());
        edges.add(edge);
      }
      result.put("edges", edges);
    }
    return result;
  }

  /**
   * 保存设计器数据
   *
   * <p>设计器拖拽 / 修改节点后保存。仅允许编辑未发布定义。
   * 支持批量更新节点坐标、节点名称、权限标识、扩展字段。
   *
   * @param definitionId 流程定义 ID
   * @param designerData 设计器数据 Map（含 {@code nodes} 数组）
   * @throws SysException {@code NOT_FOUND} — 流程定义不存在；{@code BAD_REQUEST} — 已发布
   */
  @Transactional(rollbackFor = Exception.class)
  public void saveDesignerData(String definitionId, Map<String, Object> designerData) {
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    if (def.getIsPublish() != null && def.getIsPublish() == 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("已发布的流程定义不可编辑，请先创建新版本")
          .build();
    }

    List<Map<String, Object>> nodes = MapUtils.getListOfMaps(designerData, "nodes");
    if (nodes != null) {
      for (Map<String, Object> nodeData : nodes) {
        String nodeCode = (String) nodeData.get("nodeCode");
        if (nodeCode == null) {
          continue;
        }
        Object coord = nodeData.get("coordinate");
        if (coord != null) {
          String coordStr = coord instanceof String ? (String) coord : YdszJson.toJson(coord);
          FlowNodeVO nodeForCoord = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
          if (nodeForCoord != null) {
            nodeForCoord.setCoordinate(coordStr);
            nodeRepository.save(nodeForCoord);
          }
        }
        Object nodeName = nodeData.get("nodeName");
        if (nodeName != null) {
          FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
          if (node != null) {
            node.setNodeName((String) nodeName);
            Object permFlag = nodeData.get("permissionFlag");
            if (permFlag != null) {
              node.setPermissionFlag((String) permFlag);
            }
            Object ext = nodeData.get("ext");
            if (ext != null) {
              node.setExt(ext instanceof String ? (String) ext : YdszJson.toJson(ext));
            }
            nodeRepository.save(node);
          }
        }
      }
    }

    flowDefinitionCacheService.evict(definitionId);
    log.info(
        "[Flow] 设计器数据已保存: definitionId={} nodes={}",
        definitionId,
        nodes != null ? nodes.size() : 0);
  }

  /**
   * 获取节点表单字段配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 表单字段配置 JSON 字符串；节点未配置时返回 {@code null}
   * @throws SysException {@code NOT_FOUND} — 节点不存在
   */
  @Transactional(readOnly = true)
  public String getFormConfig(String definitionId, String nodeCode) {
    FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
          .build();
    }
    return node.getFormFieldsConfig();
  }

  /**
   * 保存节点表单字段配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param formFieldsConfig 表单字段配置 JSON 字符串
   * @throws SysException {@code NOT_FOUND} — 节点不存在
   */
  @Transactional(rollbackFor = Exception.class)
  public void saveFormConfig(String definitionId, String nodeCode, String formFieldsConfig) {
    FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
          .build();
    }
    node.setFormFieldsConfig(formFieldsConfig);
    nodeRepository.save(node);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 表单字段配置已保存: definitionId={} nodeCode={}", definitionId, nodeCode);
  }

  /**
   * 获取节点 SLA 配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return SLA 配置 JSON 字符串；未配置返回 {@code null}
   * @throws SysException {@code NOT_FOUND} — 节点不存在
   */
  @Transactional(readOnly = true)
  public String getSlaConfig(String definitionId, String nodeCode) {
    FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
          .build();
    }
    return node == null ? null : node.getSlaConfigJson();
  }

  /**
   * 保存节点 SLA 配置
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param slaConfig SLA 配置 JSON 字符串
   * @throws SysException {@code NOT_FOUND} — 节点不存在
   */
  @Transactional(rollbackFor = Exception.class)
  public void saveSlaConfig(String definitionId, String nodeCode, String slaConfig) {
    FlowNodeVO node = nodeRepository.findByCode(definitionId, nodeCode).orElse(null);
    if (node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("节点不存在: definitionId=" + definitionId + " nodeCode=" + nodeCode)
          .build();
    }
    node.setSlaConfig(slaConfig);
    nodeRepository.save(node);
    flowDefinitionCacheService.evict(definitionId);
    log.info(
        "[Flow] SLA 配置已保存: definitionId={} nodeCode={} slaConfig={}",
        definitionId,
        nodeCode,
        slaConfig);
  }

  /**
   * 加锁流程定义（设计器协同编辑）
   *
   * <p>采用 CAS 乐观锁实现，保证多用户并发加锁的强一致性。
   *
   * @param definitionId 流程定义 ID
   * @param userId 当前操作用户 ID
   * @return true=加锁成功
   * @throws SysException 当锁被他人持有时
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean lockDefinition(String definitionId, String userId) {
    if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.designer.params.required")
          .build();
    }
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.designer.definition.not.found")
          .params(definitionId)
          .build();
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime timeoutExpired = now.minusMinutes(flowProperties.getDesignerLockTimeoutMinutes());

    int affected =
        definitionRepository.casLock(
            definitionId, userId, now, userId, timeoutExpired, def.getRevision());

    if (affected == 1) {
      log.info(
          "[Flow] 设计器加锁成功: defId={} userId={} timeout={}min",
          definitionId,
          userId,
          flowProperties.getDesignerLockTimeoutMinutes());
      return true;
    }

    FlowDefinitionVO latest = definitionRepository.findById(definitionId).orElse(null);
    if (latest == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.designer.definition.not.found")
          .params(definitionId)
          .build();
    }
    String holder = latest.getLockedBy();
    if (StringUtils.hasText(holder) && !holder.equals(userId)) {
      boolean expired =
          latest.getLockedAt() != null && latest.getLockedAt().isBefore(timeoutExpired);
      if (expired) {
        log.warn("[Flow] 设计器加锁重试（锁已超时但 version 变化）: defId={} holder={}", definitionId, holder);
        int retry =
            definitionRepository.casLock(
                definitionId, userId, now, userId, timeoutExpired, latest.getRevision());
        if (retry == 1) {
          log.info("[Flow] 设计器加锁成功（重试）: defId={} userId={} 抢占自={}", definitionId, userId, holder);
          return true;
        }
      }
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.designer.lock.held")
          .params(holder)
          .build();
    }
    throw SysException.builder()
        .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.designer.lock.failed")
        .build();
  }

  /**
   * 解锁流程定义（设计器协同编辑）
   *
   * <p>仅持锁人本人可解锁；他人持锁或未锁定时抛 SysException。
   *
   * @param definitionId 流程定义 ID
   * @param userId 当前操作用户 ID
   * @return true=解锁成功
   * @throws SysException 当非持锁人尝试解锁时
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean unlockDefinition(String definitionId, String userId) {
    if (!StringUtils.hasText(definitionId) || !StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.designer.params.required")
          .build();
    }
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.designer.definition.not.found")
          .params(definitionId)
          .build();
    }

    if (!StringUtils.hasText(def.getLockedBy())) {
      log.debug("[Flow] 设计器解锁：当前未锁定，幂等返回 defId={}", definitionId);
      return true;
    }

    int affected = definitionRepository.casUnlock(definitionId, userId, def.getRevision());
    if (affected == 1) {
      log.info("[Flow] 设计器解锁成功: defId={} userId={}", definitionId, userId);
      return true;
    }

    FlowDefinitionVO latest = definitionRepository.findById(definitionId).orElse(null);
    if (latest == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.designer.definition.not.found")
          .params(definitionId)
          .build();
    }
    String holder = latest.getLockedBy();
    if (StringUtils.hasText(holder) && !holder.equals(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.designer.unlock.no.permission")
          .params(holder)
          .build();
    }
    log.info("[Flow] 设计器解锁：锁已被并发清空，视为成功 defId={} userId={}", definitionId, userId);
    return true;
  }

  /**
   * 查询流程定义的锁定状态。
   *
   * @param definitionId 流程定义 ID
   * @return 锁定状态 Map
   */
  public Map<String, Object> getLockStatus(String definitionId) {
    if (!StringUtils.hasText(definitionId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.designer.params.required")
          .build();
    }
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
      return null;
    }

    Map<String, Object> result = new LinkedHashMap<>(16);
    boolean locked = StringUtils.hasText(def.getLockedBy());
    boolean expired = false;
    if (locked && def.getLockedAt() != null) {
      LocalDateTime timeoutExpired =
          LocalDateTime.now().minusMinutes(flowProperties.getDesignerLockTimeoutMinutes());
      expired = def.getLockedAt().isBefore(timeoutExpired);
    }
    result.put("locked", locked);
    result.put("lockedBy", def.getLockedBy());
    result.put("lockedAt", def.getLockedAt());
    result.put("expired", expired);
    return result;
  }
}
