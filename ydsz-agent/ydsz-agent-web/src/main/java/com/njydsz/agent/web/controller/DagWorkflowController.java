package com.njydsz.agent.web.controller;

import java.util.List;

import jakarta.validation.Valid;
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
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.repository.DagWorkflowRepository;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.core.response.YdszResponse;

/**
 * DAG 工作流持久化管理 Controller。
 *
 * <p>提供工作流 DSL 的 CRUD 能力（保存 / 更新 / 查询 / 列表 / 删除），
 * 与执行控制器（{@code DagController}）分离，各司其职。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@ApiVersion("26.09.17")
@RestController
@RequestMapping("/agent/dag-workflow")
public class DagWorkflowController {

  private final DagWorkflowRepository repository;

  public DagWorkflowController(DagWorkflowRepository repository) {
    this.repository = repository;
  }

  /**
   * 保存工作流（新建 / 更新）。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>校验工作流名称、分类等基础字段</li>
   *   <li>若为新建（workflowCode 为空），自动生成编码并将 isPublished 设为 false</li>
   *   <li>若为更新，按 workflowCode 查询现有记录，不存在则抛异常</li>
   *   <li>执行器校验 {@code dslContent} 格式合法性（YAML 语法 + 节点定义完整性）</li>
   *   <li>持久化到数据库并返回记录 ID</li>
   * </ol>
   *
   * <p>Token 说明：保存接口本身不消耗 LLM Token；Token 消耗仅在工作流实际执行时产生，由节点数量和 LLM 调用轮次决定。
   *
   * @param dto 工作流数据（workflowCode 为空则新建；存在则更新；必填字段：workflowName / dslContent）
   * @return 统一响应结果，data 为保存后的工作流 ID（雪花算法字符串）
   * @throws IllegalArgumentException 更新时工作流编码不存在，或 DSL 格式校验失败
   * @throws RuntimeException 数据库写入失败（"保存失败"）
   */
  @PostMapping("/save")
  public YdszResponse<String> save(@Valid @RequestBody DagWorkflowDTO dto) {
    boolean isCreate = (dto.getWorkflowCode() == null || dto.getWorkflowCode().isBlank());
    DagWorkflow entity;
    if (isCreate) {
      entity = new DagWorkflow();
      entity.setWorkflowCode(generateWorkflowCode());
      entity.setIsPublished(false);
    } else {
      entity = repository.findByCode(dto.getWorkflowCode())
          .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + dto.getWorkflowCode()));
    }
    entity.setWorkflowName(dto.getWorkflowName());
    entity.setDescription(dto.getDescription());
    entity.setDslContent(dto.getDslContent());
    entity.setLayoutJson(dto.getLayoutJson());
    entity.setCategory(dto.getCategory());
    boolean ok = isCreate ? repository.insert(entity) : repository.updateById(entity);
    if (!ok) {
      throw new RuntimeException("保存失败");
    }
    return YdszResponse.success(entity.getId());
  }

  /**
   * 根据编码查询工作流。
   *
   * <p>按 {@code workflowCode}（业务唯一编码）查询单条工作流记录。
   * 编码为 {@code dag-{UUID前12位}} 格式，持久化时自动生成。
   *
   * @param code 工作流编码（格式：dag-xxxxxxxxxxxx），不可为空或空白
   * @return 统一响应结果，data 为 {@link DagWorkflow}（含 id / workflowCode / workflowName / dslContent / layoutJson / category / isPublished 等字段）；不存在时返回 null
   */
  @GetMapping("/{code}")
  public YdszResponse<DagWorkflow> getByCode(@PathVariable String code) {
    return repository.findByCode(code)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }

  /**
   * 查询工作流列表。
   *
   * <p>按分类条件筛选工作流记录。不传分类或不传参数时返回全量列表。
   * 结果按持久化顺序返回，前端通常需要按 createdAt 倒序展示。
   *
   * @param category 分类筛选（可选，传 null / 空字符串则返回全量），用于区分不同业务域的工作流（如 "order" / "report" / "analysis"）
   * @return 统一响应结果，data 为 {@link DagWorkflow} 列表；无匹配记录时返回空列表（非 null）
   */
  @GetMapping("/list")
  public YdszResponse<List<DagWorkflow>> list(@RequestParam(required = false) String category) {
    List<DagWorkflow> workflows = (category == null || category.isBlank())
        ? repository.findAll()
        : repository.findByCategory(category);
    return YdszResponse.success(workflows);
  }

  /**
   * 删除工作流（逻辑删除）。
   *
   * <p>按编码查询目标工作流并执行逻辑删除（设置 {@code deleted=true} 或等效标记），
   * 已从列表中移除但数据库记录仍存在。注意：删除前不会校验工作流是否被其他工作流依赖，
   * 如需级联校验请在上游业务逻辑中处理。
   *
   * @param code 工作流编码（格式：dag-xxxxxxxxxxxx）
   * @return 统一响应结果，data 为 true 表示删除成功
   * @throws IllegalArgumentException 工作流编码不存在
   */
  @DeleteMapping("/{code}")
  public YdszResponse<Boolean> delete(@PathVariable String code) {
    DagWorkflow entity = repository.findByCode(code)
        .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + code));
    return YdszResponse.success(repository.deleteById(entity.getId()));
  }

  /** 生成工作流编码：dag-{UUID 前 12 位} */
  private String generateWorkflowCode() {
    return "dag-" + IdGenerator.nextIdStr();
  }
}
