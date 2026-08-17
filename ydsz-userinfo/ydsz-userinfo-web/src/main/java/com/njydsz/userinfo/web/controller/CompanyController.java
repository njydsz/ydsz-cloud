package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.create.CompanyCreateDTO;
import com.njydsz.userinfo.domain.dto.update.CompanyUpdateDTO;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.server.service.CompanyService;

/**
 * 公司 Controller
 *
 * <p>提供公司的完整管理能力（CRUD）。 支持集团-子公司多级架构（{@code parentId="0"} = 顶级公司）， 一个公司可包含多个部门（通过 {@code
 * CompanyDept} 维护）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/company}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>公司全量列表查询（不翻页）
 *   <li>公司 CRUD（含 {@code companyCode} 唯一性校验）
 *   <li>支持多级父子关系（{@code parentId}）
 *   <li>删除校验（有子公司或部门时禁止删除）
 * </ul>
 *
 * <p><b>与其它模块的关联：</b>
 *
 * <ul>
 *   <li>用户多租户隔离：公司是租户的物理边界
 *   <li>财务结算：{@code ydsz_finance} 跨公司数据按公司维度归集
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交
 *   <li>写接口启用 {@link RateLimit} 接口级限流
 *   <li>写接口启用 {@link Audit} 审计日志
 *   <li>删除会校验子公司和部门引用
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.service.CompanyService 公司业务逻辑
 * @see com.njydsz.userinfo.domain.entity.Company 公司实体
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Tag(name = "公司管理", description = "公司 CRUD")
public class CompanyController {

  private final CompanyService service;

  /**
   * 查询全部公司列表（不翻页）
   *
   * <p>典型场景：公司下拉选择器、组织架构选择器。
   *
   * <p>建议业务方客户端缓存（变更频率极低）。
   *
   * @return 全部未删除公司列表
   */
  @GetMapping("/list")
  @Operation(summary = "查询全部公司列表")
  public BaseResponse<List<CompanyVO>> list() {
    return BaseResponse.success(service.list());
  }

  /**
   * 查询公司树形结构
   *
   * <p>返回全部未删除公司的树形结构，使用 {@link com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 自动构建父子关系，
   * 并填充 {@code level}/{@code path} 元数据。典型场景：集团-子公司组织架构选择器。
   *
   * @return 公司树形结构根节点列表
   * @since 1.7.0
   */
  @GetMapping("/tree")
  @Operation(summary = "查询公司树形结构")
  public BaseResponse<List<CompanyTreeVO>> tree() {
    return BaseResponse.success(service.tree());
  }

  /**
   * 根据 ID 查询公司
   *
   * @param id 公司 ID
   * @return 公司详情；不存在或已删除时返回 null
   */
  @GetMapping("/{id}")
  @Operation(summary = "根据 ID 查询公司")
  public BaseResponse<CompanyVO> getById(@PathVariable String id) {
    return BaseResponse.success(service.getById(id));
  }

  /**
   * 创建公司
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：companyCode 唯一性校验 → 写入 DB。
   *
   * <p>创建顶级公司时 {@code parentId} 应传 {@code "0"}（约定值）。
   *
   * @param dto 公司创建 DTO（companyCode / companyName / parentId / contactPhone / address）
   * @return 新创建的公司 ID
   */
  @RateLimit(resource = "userinfo.company.create", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:CompanyController:create:lock", ttlSeconds = 5)
  @PostMapping
  @Operation(summary = "创建公司")
  public BaseResponse<String> create(@Valid @RequestBody CompanyCreateDTO dto) {
    return BaseResponse.success(service.create(dto));
  }

  /**
   * 更新公司
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
   *
   * @param dto 公司更新 DTO（必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "公司管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新公司: ' + #dto.id")
  @Idempotent(key = "ydsz:userinfo:CompanyController:update:lock", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.company.update", threshold = 50)
  @PutMapping
  @Operation(summary = "更新公司")
  public BaseResponse<Boolean> update(@Valid @RequestBody CompanyUpdateDTO dto) {
    return BaseResponse.success(service.update(dto));
  }

  /**
   * 按 ID 删除公司
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>删除前置校验：
   *
   * <ul>
   *   <li>有<b>子公司</b>的公司<b>禁止删除</b>（避免悬挂引用）
   *   <li>有<b>部门关联</b>的公司<b>禁止删除</b>
   *   <li>有<b>用户关联</b>的公司<b>禁止删除</b>
   * </ul>
   *
   * <p>如需删除带子公司的公司，<b>必须先</b>递归删除/迁移子公司和部门。
   *
   * @param id 公司 ID
   * @return 是否成功
   */
  @Audit(
      module = "公司管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除公司: ' + #id")
  @RateLimit(resource = "userinfo.company.remove", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:CompanyController:remove:lock", ttlSeconds = 5)
  @DeleteMapping("/{id}")
  @Operation(summary = "删除公司")
  public BaseResponse<Boolean> remove(@PathVariable String id) {
    return BaseResponse.success(service.removeById(id));
  }
}
