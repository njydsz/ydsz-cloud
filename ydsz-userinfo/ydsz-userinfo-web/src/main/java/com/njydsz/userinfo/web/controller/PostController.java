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
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.server.service.PostService;

/**
 * 岗位 Controller
 *
 * <p>提供岗位的完整管理能力（CRUD）。 岗位是「职责维度」，描述用户做什么事（如 PM、DEV、QA），区别于角色（权限维度）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/Post}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>岗位分页/列表查询（按 {@code sortOrder} 倒序）
 *   <li>岗位 CRUD（含 {@code postCode} 唯一性校验）
 *   <li>岗位删除校验（有用户关联时禁止删除）
 * </ul>
 *
 * <p><b>与其它模块的关联：</b>岗位与工作流审批人展开（{@code position:xxx}）联动， 岗位编码（{@code postCode}）变更会影响所有引用该岗位的工作流节点。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>读接口无防护，业务方可高频调用
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see PostService 岗位业务逻辑
 * @see com.njydsz.userinfo.domain.vo.PostVO 岗位VO
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller（兼任岗位维护）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/post")
@RequiredArgsConstructor
@Tag(name = "岗位管理", description = "岗位 CRUD")
public class PostController {

  private final PostService service;

  /**
   * 查询全部岗位列表（不翻页）
   *
   * <p>按 {@code sortOrder} 倒序、{@code id} 升序排列。
   *
   * <p>典型场景：用户编辑页的「岗位」下拉选择器、岗位多选框。
   *
   * <p>建议业务方客户端缓存（变更频率极低）。
   *
   * @return 全部未删除岗位列表
   */
  @GetMapping("/list")
  @Operation(summary = "查询全部岗位列表")
  public YdszResponse<List<PostVO>> list() {
    return YdszResponse.success(service.list());
  }

  /**
   * 根据 ID 查询岗位
   *
   * @param id 岗位 ID
   * @return 岗位详情；不存在或已删除时返回 null
   */
  @GetMapping("/{id}")
  @Operation(summary = "根据 ID 查询岗位")
  public YdszResponse<PostVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 创建岗位
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：postCode 唯一性校验 → 写入 DB。
   *
   * <p>创建后通过 {@code NameAssembler} 富化字段可立即被其它模块引用。
   *
   * @param dto 岗位创建 DTO（postCode / postName / sortOrder / status）
   * @return 新创建的岗位 ID
   */
  @Audit(
      module = "岗位管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建岗位: ' + #dto.postName")
  @Idempotent(key = "ydsz:userinfo:PostController:create:lock", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.Post.create", threshold = 50)
  @PostMapping
  @Operation(summary = "创建岗位")
  public YdszResponse<String> create(@Valid @RequestBody PostDTO dto) {
    return YdszResponse.success(service.create(dto));
  }

  /**
   * 更新岗位
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
   *
   * <p>修改 {@code postCode} 会同步影响工作流 {@code position:xxx} 节点解析，<b>需谨慎</b>。
   *
   * @param dto 岗位更新 DTO（必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "岗位管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新岗位: ' + #dto.id")
  @Idempotent(key = "ydsz:userinfo:PostController:update:lock", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.Post.update", threshold = 50)
  @PutMapping
  @Operation(summary = "更新岗位")
  public YdszResponse<Boolean> update(@Valid @RequestBody PostDTO dto) {
    return YdszResponse.success(service.update(dto));
  }

  /**
   * 按 ID 删除岗位
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>删除前置校验：
   *
   * <ul>
   *   <li>有<b>用户关联</b>的岗位<b>禁止删除</b>（避免悬挂引用）
   *   <li>有<b>工作流节点引用</b>的岗位<b>禁止删除</b>
   * </ul>
   *
   * <p>如需删除被引用的岗位，<b>必须先</b>迁移用户并修改工作流节点配置。
   *
   * @param id 岗位 ID
   * @return 是否成功
   */
  @Audit(
      module = "岗位管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除岗位: ' + #id")
  @RateLimit(resource = "userinfo.Post.remove", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:PostController:remove:lock", ttlSeconds = 5)
  @DeleteMapping("/{id}")
  @Operation(summary = "删除岗位")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }
}
