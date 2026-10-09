package com.njydsz.cronjob.web.controller.designer;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.util.id.IdGenerator;

/**
 * DAG 可视化设计器 Controller（P2-2 + P1-4 加载接口）。
 *
 * <p>提供 DAG 工作流的创建、编辑、保存、加载能力，配合前端 AntV X6 画布组件实现可视化编排。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>节点拖拽：从侧边栏拖拽任务/审批/子工作流节点到画布
 *   <li>连线编排：节点间连线定义执行顺序
 *   <li>属性配置：选中节点后配置 KEY、参数、审批人等属性
 *   <li>自动布局：一键整理节点位置
 *   <li>保存/加载：持久化 DAG 定义到内存存储（后续替换为 DB）
 * </ul>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>{@code POST /cronjob/dag/save}           — 保存/更新 DAG 定义</li>
 *   <li>{@code GET  /cronjob/dag/{dagKey}}       — 加载 DAG 定义（P1-4 新增）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Tag(name = "DAG 设计器", description = "DAG 工作流可视化编排")
@Slf4j
@ApiVersion("26.10.09")
@RestController
@RequestMapping("/cronjob/dag")
public class DagDesignerController {

  /** DAG 定义内存存储（dagKey → 定义 DTO，后续替换为 DB 持久化） */
  private static final ConcurrentHashMap<String, DagDefinitionDTO> DAG_STORE = new ConcurrentHashMap<>();

  /**
   * 保存 DAG 定义。
   *
   * <p>接收前端 X6 画布序列化的节点和边数据，持久化到内存存储。
   * 如果 dagKey 已存在则更新，否则新建。
   *
   * @param dto DAG 定义数据（含 nodes 和 edges）
   * @return 保存结果（含 dagKey）
   */
  @Operation(summary = "保存 DAG 定义")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
  @Audit(
      module = "DAG设计器",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'dagSave'")
  @PostMapping("/save")
  public YdszResponse<String> save(@Valid @RequestBody DagDefinitionDTO dto) {
    String dagKey = dto.getDagKey();
    if (dagKey == null || dagKey.isBlank()) {
      // 从第一个节点提取 key 作为 dagKey
      dagKey = dto.getNodes() != null && !dto.getNodes().isEmpty()
          ? dto.getNodes().get(0).getJobKey()
          : "dag-" + IdGenerator.nextIdStr();
      dto.setDagKey(dagKey);
    }
    DagDefinitionDTO toSave = new DagDefinitionDTO();
    toSave.setDagKey(dto.getDagKey());
    toSave.setNodes(dto.getNodes());
    toSave.setEdges(dto.getEdges());
    DAG_STORE.put(dagKey, toSave);
    log.info("[DagDesigner] saved DAG: dagKey={}, nodes={}, edges={}",
        dagKey,
        toSave.getNodes() != null ? toSave.getNodes().size() : 0,
        toSave.getEdges() != null ? toSave.getEdges().size() : 0);
    return YdszResponse.success(dagKey);
  }

  /**
   * 加载已保存的 DAG 定义（P1-4）。
   *
   * <p>前端打开设计器编辑时先调用此接口获取已有画布数据，填充 X6 画布。
   *
   * @param dagKey DAG 定义键
   * @return DAG 定义 DTO（含 nodes/edges）
   */
  @Operation(summary = "加载 DAG 定义")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/{dagKey}")
  public YdszResponse<DagDefinitionDTO> load(@PathVariable String dagKey) {
    DagDefinitionDTO dto = DAG_STORE.get(dagKey);
    if (dto == null) {
      log.warn("[DagDesigner] DAG not found: dagKey={}", dagKey);
      return YdszResponse.error("B60001", "DAG 定义不存在: " + dagKey);
    }
    log.info("[DagDesigner] loaded DAG: dagKey={}, nodes={}, edges={}",
        dagKey,
        dto.getNodes() != null ? dto.getNodes().size() : 0,
        dto.getEdges() != null ? dto.getEdges().size() : 0);
    return YdszResponse.success(dto);
  }

  // ======================== DTOs ========================

  /**
   * DAG 定义 DTO（前端 X6 画布序列化数据）。
   */
  @Data
  public static class DagDefinitionDTO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** DAG 定义键（唯一标识） */
    private String dagKey;

    /** 节点列表 */
    private List<NodeDTO> nodes;

    /** 边列表 */
    private List<EdgeDTO> edges;
  }

  /**
   * 节点 DTO。
   */
  @Data
  public static class NodeDTO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 节点 KEY */
    private String jobKey;
    /** 显示名称 */
    private String label;
    /** 节点类型（TASK/APPROVAL） */
    private String nodeType;
    /** X 坐标 */
    private int x;
    /** Y 坐标 */
    private int y;
    /** 审批人（APPROVAL 节点） */
    private String approvalUsers;
    /** 审批超时分钟（APPROVAL 节点） */
    private Integer approvalTimeoutMinutes;
    /** 子工作流 DAG KEY（已废弃，保留用于反序列化兼容） */
    private String subWorkflowDagKey;
    /** 节点级参数 JSON */
    private String paramsJson;
  }

  /**
   * 边 DTO。
   */
  @Data
  public static class EdgeDTO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 源节点 ID */
    private String source;
    /** 目标节点 ID */
    private String target;
  }
}
