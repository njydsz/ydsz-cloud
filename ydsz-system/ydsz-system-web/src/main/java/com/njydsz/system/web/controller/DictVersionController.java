package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * 字典版本 Controller
 *
 * <p>提供字典变更历史查询和回滚能力。字典版本是字典变更审计与回滚能力的数据基础： 每次字典项发生变更（save / updateById / removeById）时，{@link
 * com.njydsz.system.server.service.DictItemServiceImpl} 会自动创建一条版本快照（含变更时间、操作说明、变更前字典全量 JSON 快照）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/dict/version}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>变更历史查询</b>：按 typeCode 查询该类型下所有版本快照，按 version 倒序
 *   <li><b>回滚操作</b>：一键回滚到指定历史版本（创建新版本标记回滚来源，保持审计链完整）
 *   <li><b>回滚审计</b>：运维/审计场景定位「字典项为什么突然变了」
 *   <li><b>合规留存</b>：字典变更符合「变更留痕」合规要求
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>回滚接口启用 {@link Idempotent} 防重复提交
 *   <li>回滚接口启用 {@link RateLimit} 接口级限流（10 QPS，低频危险操作）
 *   <li>回滚接口启用 {@link Audit} 审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.EntityVersionService 统一实体版本业务逻辑
 * @see com.njydsz.system.infra.entity.EntityVersion 实体版本
 */
@Tag(name = "字典版本", description = "字典变更历史查询 + 一键回滚")
@RestController
@RequestMapping("/api/v1/dict/version")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:dict:version:list")
public class DictVersionController {

  private final EntityVersionService entityVersionService;

  private final DictItemService dictItemService;

  /** 分页安全上限 */
  private static final int MAX_PAGE_SIZE = 500;

  /**
   * 按字典类型编码查询版本历史。
   *
   * <p>返回该 typeCode 下所有版本快照，按 version 倒序（最新版本在前）。 每条记录含变更时间、操作人、操作说明、变更前全量 JSON 快照。
   *
   * @param typeCode 字典类型编码（如 {@code "order_status"}）
   * @return 版本历史列表（可能为空）
   */
  @Operation(summary = "按类型编码查询版本历史")
  @GetMapping("/{typeCode}")
  public YdszResponse<List<EntityVersionVO>> listByTypeCode(@PathVariable String typeCode) {
    return YdszResponse.success(
        entityVersionService.listByResourceTypeAndKey(
            EntityVersionService.RESOURCE_TYPE_DICT, typeCode));
  }

  /**
   * 按类型编码分页查询版本历史（P2-3 分页优化）。
   *
   * <p>适用于版本量大的场景（如高频变更的字典），支持翻页查询，避免一次性加载全部版本。
   *
   * @param query 分页查询条件（resourceType=DICT / resourceKey=typeCode / pageNum / pageSize）
   * @return 分页结果（含总记录数）
   */
  @Operation(summary = "按类型编码分页查询版本历史")
  @GetMapping("/page")
  public PageResponse<List<EntityVersionVO>> pageByTypeCode(EntityVersionPageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return entityVersionService.pageByResourceTypeAndKey(query);
  }

  /**
   * 执行字典版本回滚
   *
   * <p>将指定字典类型回滚到目标版本，执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在
   *   <li>查询当前字典项作为回滚前快照（用于审计）
   *   <li>物理删除当前字典项
   *   <li>从目标快照重建字典项
   *   <li>创建新版本记录（标记回滚来源）
   * </ol>
   *
   * <p><b>幂等保护：</b>同一 typeCode + targetVersion 组合 30 秒内不可重复提交。
   *
   * <p><b>限流：</b>全局 10 QPS（低频危险操作）。
   *
   * @param typeCode 字典类型编码
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID（来自请求头 X-User-Id）
   * @return 新创建的回滚版本 ID
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'回滚字典: ' + #typeCode + ' → ' + #targetVersion")
  @Operation(summary = "回滚字典到指定版本", description = "将字典回滚到历史版本，操作不可撤销")
  @RateLimit(resource = "system.dict.rollback", threshold = 10)
  @Idempotent(
      key = "'ydsz:system:DictVersionController:rollback:' + #typeCode + ':' + #targetVersion",
      ttlSeconds = 30)
  @PostMapping("/{typeCode}/rollback")
  public YdszResponse<String> rollback(
      @Parameter(description = "字典类型编码") @PathVariable @NotBlank String typeCode,
      @Parameter(description = "目标版本号") @RequestParam @NotBlank String targetVersion,
      @Parameter(description = "操作人 ID")
          @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false)
          String operatorId) {
    return YdszResponse.success(
        dictItemService.rollbackTo(typeCode, targetVersion, operatorId));
  }
}
