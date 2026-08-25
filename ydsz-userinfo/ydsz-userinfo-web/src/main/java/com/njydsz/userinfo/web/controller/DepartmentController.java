package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.server.service.DepartmentService;

/**
 * 部门 Controller
 *
 * <p>提供部门的完整管理能力（CRUD）、部门树形结构查询。 部门是组织架构的核心节点，支持无限级树形结构（{@code parentId="0"} = 根部门）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/dept}
 *
 * <p><b>安全特性：</b>写接口启用 {@link Idempotent} 防重复、{@link RateLimit} 限流、{@link Audit} 审计日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.service.DepartmentService 部门业务逻辑
 */
@Slf4j
@RequestMapping("/api/v1/dept")
@RequiredArgsConstructor
@Tag(name = "部门管理", description = "部门 CRUD、树形结构查询")
public class DepartmentController {

  private final DepartmentService service;

  /**
   * 查询全部部门列表（扁平结构）
   *
   * <p>返回全量部门 VO（不构建树形结构），适用于需要扁平数据 + 客户端自行构建树的场景。
   *
   * <p>按 {@code sort_order} 升序、{@code id} 升序排列。
   *
   * @return 全部未删除部门列表
   */
  @GetMapping("/list")
  @Operation(summary = "查询全部部门列表")
  public YdszResponse<List<DepartmentVO>> list() {
    return YdszResponse.success(service.list());
  }

  /**
   * 查询部门树形结构
   *
   * <p>返回根部门（{@code parentId="0"} 或 {@code null}）开始的多级嵌套树。
   *
   * <p>典型场景：组织架构选择器、用户管理中的部门选择树。
   *
   * <p>由 Service 层在内存中构建树（递归遍历全量列表），<b>不推荐</b>前端自行构建。
   *
   * @return 部门树形结构列表（每个节点含 children）
   */
  @GetMapping("/tree")
  @Operation(summary = "查询部门树形结构")
  public YdszResponse<List<DepartmentTreeVO>> tree() {
    return YdszResponse.success(service.tree());
  }

  /**
   * 根据 ID 查询部门
   *
   * @param id 部门 ID
   * @return 部门详情；不存在或已删除时返回 null
   */
  @GetMapping("/{id}")
  @Operation(summary = "根据 ID 查询部门")
  public YdszResponse<DepartmentVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 创建部门
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：deptCode 唯一性校验 → 写入 DB → 触发树形缓存失效。
   *
   * <p>创建根部门时 {@code parentId} 应传 {@code "0"}（约定值）。
   *
   * @param dto 部门创建 DTO（deptCode / deptName / parentId / sortOrder / status）
   * @return 新创建的部门 ID
   */
  @RateLimit(resource = "userinfo.Department.create", threshold = 50)
  @Audit(
      module = "部门管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建部门: ' + #dto.deptName")
  @Idempotent(key = "ydsz:userinfo:DepartmentController:create:lock", ttlSeconds = 5)
  @PostMapping
  @Operation(summary = "创建部门")
  public YdszResponse<String> create(@Valid @RequestBody DepartmentDTO dto) {
    return YdszResponse.success(service.create(dto));
  }

  /**
   * 更新部门
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
   *
   * <p>修改 {@code parentId} 会触发整棵子树路径重算（由 Service 层处理）。
   *
   * @param dto 部门更新 DTO（必须包含 ID）
   * @return 是否成功
   */
  @RateLimit(resource = "userinfo.Department.update", threshold = 50)
  @Audit(
      module = "部门管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新部门: ' + #dto.id")
  @Idempotent(key = "ydsz:userinfo:DepartmentController:update:lock", ttlSeconds = 5)
  @PutMapping
  @Operation(summary = "更新部门")
  public YdszResponse<Boolean> update(@Valid @RequestBody DepartmentDTO dto) {
    return YdszResponse.success(service.update(dto));
  }

  /**
   * 按 ID 删除部门
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>删除前置校验：
   *
   * <ul>
   *   <li>有<b>子部门</b>的部门<b>禁止删除</b>（避免悬挂引用）
   *   <li>有<b>用户关联</b>的部门<b>禁止删除</b>
   * </ul>
   *
   * <p>如需删除带子部门的部门，<b>必须先</b>递归删除/迁移子部门和用户。
   *
   * @param id 部门 ID
   * @return 是否成功
   */
  @RateLimit(resource = "userinfo.Department.remove", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:DepartmentController:remove:lock", ttlSeconds = 5)
  @DeleteMapping("/{id}")
  @Operation(summary = "删除部门")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }
}
