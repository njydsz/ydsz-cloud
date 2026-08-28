package com.njydsz.workflow.server.service.impl.definition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
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
import com.njydsz.workflow.server.engine.FlowGraphValidator;

/**
 * 流程定义部署管理器
 *
 * <p>承担流程定义<b>部署</b>相关全部职责：双模式部署（BPMN XML / 轻量 JSON）、拓扑校验、
 * 三方写入（definition + node + skip）、BPMN 2.0 zip 包批量部署。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>双模式部署</b>：支持 BPMN 2.0 标准 XML（{@code bpmnXml}）与轻量 JSON（{@code nodes+skips}）两种模型，
 *       通过 {@link BpmnXmlParser} 解析后统一转写为 {@link FlowNodeVO} / {@link FlowSkipVO} 值对象
 *   <li><b>拓扑校验</b>：部署前调用 {@link FlowGraphValidator} 校验连通性、死节点、环路口等结构规则，
 *       校验失败立即阻断写入
 *   <li><b>三方写入</b>：{@code ydsz_flow_definition + ydsz_flow_node + ydsz_flow_skip} 事务原子性
 *   <li><b>批量部署</b>：BPMN 2.0 zip 包批量部署，每个文件独立事务
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>{@link #deploy} 开启 {@code @Transactional(rollbackFor = Exception.class)}，确保三方写入原子性
 *   <li>{@link #batchDeployFromZip} 通过 {@code self} 代理引用调用 {@link #deploy}，每个文件独立事务
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FlowDefinitionDeployManager {

    /** 文件读取缓冲区大小 */
  private static final int BUFFER_SIZE = 4096;

  /** 流程定义仓储 */
  private final FlowDefinitionRepository definitionRepository;

  /** 流程节点仓储 */
  private final FlowNodeRepository nodeRepository;

  /** 节点跳转仓储 */
  private final FlowSkipRepository skipRepository;

  /** BPMN 2.0 XML 解析器 */
  private final BpmnXmlParser bpmnXmlParser;

  /** 流程图结构校验器 */
  private final FlowGraphValidator graphValidator;

  /** 流程定义元数据缓存 */
  private final FlowDefinitionCacheService flowDefinitionCacheService;

  /** 统一配置属性 */
  private final FlowProperties flowProperties;

  /**
   * 自注入代理引用，使 {@link #batchDeployFromZip} 内部调用 {@link #deploy} 时能正确触发 Spring 事务代理。
   * 使用 {@code @Lazy} 打破启动期循环依赖。
   */
  private final FlowDefinitionDeployManager self;

  public FlowDefinitionDeployManager(
      FlowDefinitionRepository definitionRepository,
      FlowNodeRepository nodeRepository,
      FlowSkipRepository skipRepository,
      BpmnXmlParser bpmnXmlParser,
      FlowGraphValidator graphValidator,
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowProperties flowProperties,
      @Lazy FlowDefinitionDeployManager self) {
    this.definitionRepository = definitionRepository;
    this.nodeRepository = nodeRepository;
    this.skipRepository = skipRepository;
    this.bpmnXmlParser = bpmnXmlParser;
    this.graphValidator = graphValidator;
    this.flowDefinitionCacheService = flowDefinitionCacheService;
    this.flowProperties = flowProperties;
    this.self = self;
  }

  /**
   * 部署流程定义（双模式：BPMN XML / 轻量 JSON）
   *
   * <p>完整执行链路：
   *
   * <ol>
   *   <li><b>参数校验</b>：必填 {@code flowCode / flowName}，至少二选一传 {@code bpmnXml / nodes}
   *   <li><b>租户解析</b>：{@code dto.tenantId} → {@code SecurityContext} → 默认 {@code "1"}
   *   <li><b>重名校验</b>：同 {@code flowCode+version+tenantId} 已存在时抛 {@code DUPLICATE_KEY}
   *   <li><b>模型解析</b>：XML 模式通过 {@link BpmnXmlParser#parse} 解析；JSON 模式直接构造
   *   <li><b>结构校验</b>：{@link FlowGraphValidator#validate} 校验连通性、死节点、环路口
   *   <li><b>三方写入</b>：definition + node + skip 事务原子性
   *   <li><b>缓存清理</b>：{@code @CacheEvict} + {@link FlowDefinitionCacheService#evict}
   * </ol>
   *
   * @param dto 部署 DTO（含 {@code flowCode/flowName/version/bpmnXml/nodes/skips/tenantId}）
   * @return 新流程定义的 ID
   * @throws SysException {@code BAD_REQUEST} — 参数缺失或结构校验失败；{@code DUPLICATE_KEY} — 版本冲突
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public String deploy(FlowDeployProcessDTO dto) {
    validateDeployParams(dto);
    String version = StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0";
    String tenantId = dto.getTenantId() != null ? dto.getTenantId() : AuthContextUtils.getTenantIdOrDefault();
    checkVersionConflict(dto.getFlowCode(), version, tenantId);

    boolean hasBpmn = StringUtils.hasText(dto.getBpmnXml());
    boolean hasJson = dto.getNodes() != null && !dto.getNodes().isEmpty();
    if (!hasBpmn && !hasJson) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("bpmnXml / nodes 至少二选一")
          .build();
    }

    List<FlowNodeVO> nodes;
    List<FlowSkipVO> skips;
    if (hasBpmn) {
      nodes = parseBpmnNodes(dto, version);
      skips = parseBpmnSkips(dto);
    } else {
      nodes = parseJsonNodes(dto);
      skips = parseJsonSkips(dto);
    }

    graphValidator.validate(nodes, skips);

    FlowDefinitionVO savedDef = saveDefinition(dto, version, tenantId);
    String definitionId = savedDef.getId();
    saveNodes(nodes, definitionId, dto.getFlowCode(), tenantId, dto.getProviderTraceId());
    saveSkips(skips, definitionId, dto.getFlowCode(), tenantId, dto.getProviderTraceId());

    log.info("[Flow] 部署流程成功: code={} version={} defId={} mode={} nodes={} skips={}",
        dto.getFlowCode(), version, definitionId, hasBpmn ? "BPMN" : "JSON",
        nodes.size(), skips.size());
    flowDefinitionCacheService.evict(definitionId);
    return definitionId;
  }

  /**
   * 校验部署参数：flowCode/flowName 不能为空。
   *
   * @param dto 参数说明
   */
  private void validateDeployParams(FlowDeployProcessDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getFlowCode()) || !StringUtils.hasText(dto.getFlowName())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("flowCode/flowName 不能为空")
          .build();
    }
  }

  /**
   * 校验版本冲突：flowCode + version + tenantId 组合唯一。
   *
   * @param flowCode 参数说明
   * @param version 参数说明
   * @param tenantId 参数说明
   */
  private void checkVersionConflict(String flowCode, String version, String tenantId) {
    FlowDefinitionVO existing = definitionRepository.findPublished(flowCode, version, tenantId)
        .orElse(null);
    if (existing != null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("流程定义已存在: code=" + flowCode + " version=" + version)
          .build();
    }
  }

  /**
   * BPMN 模式下解析节点列表，注入节点坐标。
   *
   * @param dto 参数说明
   * @param version 参数说明
   * @return 返回值说明
   */
  private List<FlowNodeVO> parseBpmnNodes(FlowDeployProcessDTO dto, String version) {
    BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
    if (StringUtils.hasText(bpmnModel.getProcessId())
        && !bpmnModel.getProcessId().equals(dto.getFlowCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("BPMN process id 与 flowCode 不一致: bpmn=" + bpmnModel.getProcessId()
              + " dto=" + dto.getFlowCode())
          .build();
    }
    if (!StringUtils.hasText(dto.getFlowName()) || dto.getFlowName().equals(dto.getFlowCode())) {
      dto.setFlowName(bpmnModel.getProcessName());
    }
    List<FlowNodeVO> nodes = bpmnModel.getNodes();
    injectNodeCoordinates(nodes, bpmnModel);
    return nodes;
  }

  /**
   * 向节点列表注入 BPMNDI 坐标信息。
   *
   * @param nodes 参数说明
   * @param bpmnModel 参数说明
   */
  private void injectNodeCoordinates(List<FlowNodeVO> nodes, BpmnModel bpmnModel) {
    Map<String, BpmnModel.NodeCoordinate> nodeCoords = bpmnModel.getNodeCoordinates();
    if (nodeCoords == null || nodeCoords.isEmpty()) {
      return;
    }
    for (FlowNodeVO n : nodes) {
      BpmnModel.NodeCoordinate coord = nodeCoords.get(n.getNodeCode());
      if (coord != null) {
        n.setCoordinate(YdszJson.toJson(Map.of("x", coord.getX(), "y", coord.getY(),
            "width", coord.getWidth(), "height", coord.getHeight())));
      }
    }
    log.info("[Flow] 从 BPMNDI 注入节点坐标: defId-pending count={}", nodeCoords.size());
  }

  /**
   * BPMN 模式下解析跳转列表。
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  private List<FlowSkipVO> parseBpmnSkips(FlowDeployProcessDTO dto) {
    BpmnModel bpmnModel = bpmnXmlParser.parse(dto.getBpmnXml());
    return bpmnModel.getSkips();
  }

  /**
   * JSON 模式下解析节点列表，校验开始节点和编码唯一性。
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  private List<FlowNodeVO> parseJsonNodes(FlowDeployProcessDTO dto) {
    List<FlowNodeVO> nodes = new ArrayList<>(dto.getNodes().size());
    for (FlowDeployProcessDTO.FlowNodeDTO n : dto.getNodes()) {
      FlowNodeVO node = new FlowNodeVO();
      node.setNodeCode(n.getNodeCode());
      node.setNodeName(n.getNodeName() == null ? n.getNodeCode() : n.getNodeName());
      node.setNodeType(n.getNodeType() == null ? FlowNodeType.APPROVAL.getCode() : n.getNodeType());
      node.setPermissionFlag(n.getPermissionFlag());
      node.setSkipAnyNode(n.getSkipAnyNode());
      nodes.add(node);
    }
    validateJsonNodes(nodes);
    return nodes;
  }

  /**
   * JSON 模式下校验节点列表。
   *
   * @param nodes 参数说明
   */
  private void validateJsonNodes(List<FlowNodeVO> nodes) {
    boolean hasStart = nodes.stream().anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
    if (!hasStart) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("流程定义必须包含开始节点（nodeType=0）")
          .build();
    }
    long uniqueCount = nodes.stream().map(FlowNodeVO::getNodeCode).distinct().count();
    if (uniqueCount != nodes.size()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("节点编码 nodeCode 必须唯一")
          .build();
    }
  }

  /**
   * JSON 模式下解析跳转列表。
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  private List<FlowSkipVO> parseJsonSkips(FlowDeployProcessDTO dto) {
    List<FlowSkipVO> skips = new ArrayList<>(dto.getSkips() != null ? dto.getSkips().size() : 16);
    if (dto.getSkips() != null) {
      for (FlowDeployProcessDTO.FlowSkipDTO s : dto.getSkips()) {
        FlowSkipVO skip = new FlowSkipVO();
        skip.setSkipName(s.getSkipName());
        skip.setSkipType(StringUtils.hasText(s.getSkipType()) ? s.getSkipType() : FlowSkipType.PASS.name());
        skip.setSkipCondition(s.getSkipCondition());
        skip.setNextNodeCode(s.getToNodeCode());
        skip.setExt(YdszJson.toJson(Map.of("sourceRef", s.getFromNodeCode())));
        skips.add(skip);
      }
    }
    return skips;
  }

  /**
   * 保存流程定义。
   *
   * @param dto 参数说明
   * @param version 参数说明
   * @param tenantId 参数说明
   * @return 返回值说明
   */
  private FlowDefinitionVO saveDefinition(FlowDeployProcessDTO dto, String version, String tenantId) {
    FlowDefinitionVO def = new FlowDefinitionVO();
    def.setFlowCode(dto.getFlowCode());
    def.setFlowName(dto.getFlowName());
    def.setCategory(dto.getCategory());
    def.setFlowVersion(version);
    def.setModelValue("CLASSICS");
    def.setFormCustom("N");
    def.setFormPath(dto.getFormPath());
    def.setActivityStatus(1);
    def.setIsPublish(0);
    def.setDescription(dto.getDescription());
    def.setTenantId(tenantId);
    def.setProviderTraceId(dto.getProviderTraceId());
    return definitionRepository.save(def);
  }

  /**
   * 批量保存节点。
   *
   * @param nodes 参数说明
   * @param definitionId 参数说明
   * @param flowCode 参数说明
   * @param tenantId 参数说明
   * @param providerTraceId 参数说明
   */
  private void saveNodes(List<FlowNodeVO> nodes, String definitionId, String flowCode,
      String tenantId, String providerTraceId) {
    for (FlowNodeVO node : nodes) {
      node.setDefinitionId(definitionId);
      node.setFlowCode(flowCode);
      node.setTenantId(tenantId);
      node.setProviderTraceId(providerTraceId);
      nodeRepository.save(node);
    }
  }

  /**
   * 批量保存跳转。
   *
   * @param skips 参数说明
   * @param definitionId 参数说明
   * @param flowCode 参数说明
   * @param tenantId 参数说明
   * @param providerTraceId 参数说明
   */
  private void saveSkips(List<FlowSkipVO> skips, String definitionId, String flowCode,
      String tenantId, String providerTraceId) {
    for (FlowSkipVO skip : skips) {
      skip.setDefinitionId(definitionId);
      skip.setFlowCode(flowCode);
      skip.setTenantId(tenantId);
      skip.setProviderTraceId(providerTraceId);
      skipRepository.save(skip);
    }
  }

  /**
   * 从 BPMN 部署包 .zip 批量导入流程定义
   *
   * <p>遍历 zip 内的 {@code .bpmn} / {@code .bpmn20.xml} 文件，逐个解析并委托 {@link #deploy} 入库。
   * 单个文件失败不影响其他文件（通过 self 代理调用 deploy，每个文件独立事务）。
   *
   * @param zipBytes zip 文件字节数组
   * @param tenantId 租户 ID（可空，默认从 SecurityContext 获取）
   * @return Map 包含 successCount（成功数）和 failedItems（失败列表，每项含 fileName + reason）
   */
  public Map<String, Object> batchDeployFromZip(byte[] zipBytes, String tenantId) {
    if (zipBytes == null || zipBytes.length == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("zip 文件内容为空")
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

    int successCount = 0;
    List<Map<String, String>> failedItems = new ArrayList<>();

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String fileName = entry.getName();
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".bpmn") && !lowerName.endsWith(".bpmn20.xml")) {
          continue;
        }
        try {
          String bpmnXml = new String(readAllBytes(zis), StandardCharsets.UTF_8);
          BpmnModel model = bpmnXmlParser.parse(bpmnXml);
          String flowCode = model.getProcessId();
          String flowName =
              StringUtils.hasText(model.getProcessName())
                  ? model.getProcessName()
                  : extractBaseName(fileName);

          if (!StringUtils.hasText(flowCode)) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .message("BPMN 文件缺少 process id: " + fileName)
                .build();
          }

          FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
          dto.setFlowCode(flowCode);
          dto.setFlowName(flowName);
          dto.setVersion("1.0");
          dto.setBpmnXml(bpmnXml);
          dto.setTenantId(tid);
          self.deploy(dto);
          successCount++;
          log.info("[Flow] zip 批量导入成功: fileName={} flowCode={}", fileName, flowCode);
        } catch (Exception e) {
          Map<String, String> fail = new LinkedHashMap<>();
          fail.put("fileName", fileName);
          fail.put(
              "reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
          failedItems.add(fail);
          log.warn("[Flow] zip 批量导入失败: fileName={} reason={}", fileName, e.getMessage());
        } finally {
          zis.closeEntry();
        }
      }
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("zip 文件解析失败: " + e.getMessage())
          .build();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedItems", failedItems);
    log.info("[Flow] zip 批量导入完成: success={} failed={}", successCount, failedItems.size());
    return result;
  }

  /**
   * 读取 ZipInputStream 当前 entry 的全部字节（不关闭流）
   *
   * @param zis 参数说明
   * @return 返回值说明
   */
  private byte[] readAllBytes(ZipInputStream zis) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[BUFFER_SIZE];
    int len;
    while ((len = zis.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }

  /**
   * 从 zip entry 路径中提取文件名（去掉目录和扩展名）
   *
   * @param fileName 参数说明
   * @return 返回值说明
   */
  private String extractBaseName(String fileName) {
    String name = fileName;
    int slashIdx = name.lastIndexOf('/');
    if (slashIdx >= 0) {
      name = name.substring(slashIdx + 1);
    }
    int dotIdx = name.lastIndexOf('.');
    if (dotIdx > 0) {
      name = name.substring(0, dotIdx);
    }
    return name;
  }
}
