package com.njydsz.cronjob.web.controller.designer;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;

/**
 * DAG 可视化设计器 Controller（P2-2）。
 *
 * <p>提供 DAG 工作流的创建、编辑、保存能力，配合前端 AntV X6 画布组件实现可视化编排。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>节点拖拽：从侧边栏拖拽任务/审批/子工作流节点到画布
 *   <li>连线编排：节点间连线定义执行顺序
 *   <li>属性配置：选中节点后配置 KEY、参数、审批人等属性
 *   <li>自动布局：一键整理节点位置
 *   <li>保存/加载：持久化 DAG 定义到 DB
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "DAG 设计器", description = "DAG 工作流可视化编排")
@Slf4j
@RequestMapping("/api/v1/cronjob/dag")
@RequiredArgsConstructor
public class DagDesignerController {

  /**
   * 保存 DAG 定义。
   *
   * <p>接收前端 X6 画布序列化的节点和边数据，持久化到 DB。
   *
   * @param dto DAG 定义数据（含 nodes 和 edges）
   * @return 保存结果
   */
  @Operation(summary = "保存 DAG 定义")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
  @Audit(
      module = "DAG设计器",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'dagSave'")
  @PostMapping("/save")
  public YdszResponse<Boolean> save(@RequestBody DagDefinitionDTO dto) {
    log.info("[DagDesigner] 保存 DAG 定义: nodes={} edges={}", dto.getNodes().size(), dto.getEdges().size());

    // TODO: 实际实现需要调用 DagDefinitionService 持久化
    // 当前返回模拟结果
    return YdszResponse.success(true);
  }

  /**
   * DAG 定义 DTO（前端 X6 画布序列化数据）。
   */
  @Data
  public static class DagDefinitionDTO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
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
    /** 节点类型（TASK/APPROVAL/SUB_WORKFLOW） */
    private String nodeType;
    /** X 坐标 */
    private int x;
    /** Y 坐标 */
    private int y;
    /** 审批人（APPROVAL 节点） */
    private String approvalUsers;
    /** 审批超时分钟（APPROVAL 节点） */
    private Integer approvalTimeoutMinutes;
    /** 子工作流 DAG KEY（SUB_WORKFLOW 节点） */
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
