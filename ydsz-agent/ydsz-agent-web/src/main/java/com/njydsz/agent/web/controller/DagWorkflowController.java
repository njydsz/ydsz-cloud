package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.UUID;

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

  /** 工作流编码中 UUID 前缀截取长度。 */
  private static final int WORKFLOW_CODE_PREFIX_LENGTH = 12;

  private final DagWorkflowRepository repository;

  public DagWorkflowController(DagWorkflowRepository repository) {
    this.repository = repository;
  }

  /**
   * 保存工作流（新建 / 更新）。
   *
   * @param dto 工作流数据（workflowCode 为空则新建；存在则更新）
   * @return 保存后的工作流 ID
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
   * @param code 工作流编码
   * @return 工作流详情
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
   * @param category 分类筛选（可选）
   * @return 工作流列表
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
   * @param code 工作流编码
   * @return 操作结果
   */
  @DeleteMapping("/{code}")
  public YdszResponse<Boolean> delete(@PathVariable String code) {
    DagWorkflow entity = repository.findByCode(code)
        .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + code));
    return YdszResponse.success(repository.deleteById(entity.getId()));
  }

  /** 生成工作流编码：dag-{UUID 前 12 位} */
  private String generateWorkflowCode() {
    return "dag-" + UUID.randomUUID().toString().replace("-", "").substring(0, WORKFLOW_CODE_PREFIX_LENGTH);
  }
}
