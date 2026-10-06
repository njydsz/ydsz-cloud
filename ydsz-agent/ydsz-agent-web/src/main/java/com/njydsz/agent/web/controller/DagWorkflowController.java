package com.njydsz.agent.web.controller;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.dto.DagWorkflowDTO;
import com.njydsz.agent.domain.service.DagWorkflowService;
import com.njydsz.agent.domain.vo.DagWorkflowVO;
import com.njydsz.agent.web.util.ExcelExportUtil;
import com.njydsz.agent.web.vo.DagWorkflowExportVO;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * DAG 工作流持久化管理 Controller。
 *
 * <p>提供工作流 DSL 的 CRUD 能力（保存 / 更新 / 查询 / 列表 / 删除），
 * 与执行控制器（{@code DagController}）分离，各司其职。
 *
 * <p><b>DDD 分层：</b>Controller 注入 {@link DagWorkflowService}（domain 接口），
 * 禁止直接注入 Repository。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@ApiVersion("26.09.17")
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/dag-workflow")
public class DagWorkflowController {

  private final DagWorkflowService dagWorkflowService;

  /**
   * 保存工作流（新建 / 更新）。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>校验工作流名称、分类等基础字段</li>
   *   <li>若为新建（workflowCode 为空），自动生成编码并将 isPublished 设为 false</li>
   *   <li>若为更新，按 workflowCode 查询现有记录，不存在则抛业务异常</li>
   *   <li>持久化到数据库并返回记录 ID</li>
   * </ol>
   *
   * <p>Token 说明：保存接口本身不消耗 LLM Token；Token 消耗仅在工作流实际执行时产生，由节点数量和 LLM 调用轮次决定。
   *
   * @param dto 工作流数据（workflowCode 为空则新建；存在则更新；必填字段：workflowName / dslContent）
   * @return 统一响应结果，data 为保存后的工作流 ID（雪花算法字符串）
   */
  @PostMapping("/save")
  public YdszResponse<String> save(@Valid @RequestBody DagWorkflowDTO dto) {
    String id = dagWorkflowService.save(dto);
    return YdszResponse.success(id);
  }

  /**
   * 根据编码查询工作流。
   *
   * <p>按 {@code workflowCode}（业务唯一编码）查询单条工作流记录。
   * 编码为 {@code dag-{雪花ID}} 格式，持久化时自动生成。
   *
   * @param code 工作流编码（格式：dag-xxxxxxxxxxxx），不可为空或空白
   * @return 统一响应结果，data 为 {@link DagWorkflow}（含 id / workflowCode / workflowName / dsl / category 等字段）；不存在时返回 null
   */
  @GetMapping("/{code}")
  public YdszResponse<DagWorkflowVO> getByCode(@PathVariable String code) {
    return YdszResponse.success(dagWorkflowService.getByCode(code));
  }

  /**
   * 查询工作流列表。
   *
   * <p>按分类条件筛选工作流记录。不传分类或不传参数时返回全量列表。
   * 结果按更新时间倒序返回。
   *
   * @param category 分类筛选（可选，传 null / 空字符串则返回全量），用于区分不同业务域的工作流（如 "order" / "report" / "analysis"）
   * @return 统一响应结果，data 为 {@link DagWorkflow} 列表；无匹配记录时返回空列表（非 null）
   */
  @GetMapping("/list")
  public YdszResponse<List<DagWorkflowVO>> list(@RequestParam(required = false) String category) {
    return YdszResponse.success(dagWorkflowService.listByCategory(category));
  }

  /**
   * 删除工作流（逻辑删除）。
   *
   * <p>按编码查询目标工作流并执行逻辑删除（设置 isDeleted），
   * 已从列表中移除但数据库记录仍存在。注意：删除前不会校验工作流是否被其他工作流依赖，
   * 如需级联校验请在上游业务逻辑中处理。
   *
   * @param code 工作流编码（格式：dag-xxxxxxxxxxxx）
   * @return 统一响应结果，data 为 true 表示删除成功
   */
  @DeleteMapping("/{code}")
  public YdszResponse<Boolean> delete(@PathVariable String code) {
    return YdszResponse.success(dagWorkflowService.deleteByCode(code));
  }

  /**
   * 导出 DAG 工作流列表（Excel）
   *
   * <p>按当前分类过滤条件导出工作流列表，未传分类时全量导出。
   * SuperFastExcelWriter 每次 {@code doWrite} 输出完整 xlsx，禁止多次调用，因此先聚合再写入。
   * 文件名为 {@code dag_workflows_yyyyMMddHHmmss.xlsx}。
   *
   * <p>Excel 表头和列宽通过 {@link DagWorkflowExportVO} 上的 {@code @ExcelProperty} 注解定义。
   *
   * @param response HTTP 响应
   * @param category 分类筛选（同 {@link #list(java.lang.String)}）
   */
  @Operation(summary = "导出 DAG 工作流列表（Excel）")
  @GetMapping("/export")
  public void exportDagWorkflows(
      jakarta.servlet.http.HttpServletResponse response,
      @RequestParam(required = false) String category) throws java.io.IOException {
    List<DagWorkflowVO> all = dagWorkflowService.listByCategory(category);
    List<DagWorkflowExportVO> rows = new ArrayList<>(all.size());
    for (DagWorkflowVO vo : all) {
      rows.add(toExportVO(vo));
    }
    ExcelExportUtil.write(response, rows, DagWorkflowExportVO.class, "dag_workflows", "DagWorkflows");
  }

  // ==================== 私有转换方法 ====================

  /**
   * 将 {@link DagWorkflowVO} 转换为 Excel 导出行。
   *
   * @param vo DAG 工作流视图对象
   * @return Excel 导出行
   */
  private DagWorkflowExportVO toExportVO(DagWorkflowVO vo) {
    DagWorkflowExportVO export = new DagWorkflowExportVO();
    export.setWorkflowCode(vo.getWorkflowCode());
    export.setName(vo.getName());
    export.setDescription(vo.getDescription());
    export.setCategory(vo.getCategory());
    export.setId(vo.getId());
    export.setCreatedBy(vo.getCreatedBy());
    export.setCreatedAt(vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : null);
    return export;
  }
}
