package com.njydsz.system.web.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.system.domain.feature.FeatureFlagDTO;
import com.njydsz.system.domain.query.FeatureFlagPageQuery;
import com.njydsz.system.domain.vo.FeatureFlagVO;

/**
 * 特性开关管理 REST 控制器（ydsz-system-web） — P1-3 前端 CRUD 补全。
 *
 * <p>提供特性开关的创建、编辑、删除、分页查询等管理能力。
 *
 * <p><b>端点：</b>
 * <ul>
 *   <li>{@code GET    /feature-flag/page}      — 分页查询</li>
 *   <li>{@code POST   /feature-flag}           — 创建特性开关</li>
 *   <li>{@code PUT    /feature-flag}           — 更新特性开关</li>
 *   <li>{@code DELETE /feature-flag/{id}}      — 删除特性开关</li>
 * </ul>
 *
 * <p><b>存储：</b>当前基于内存 ConcurrentHashMap，后续可平滑替换为数据库持久化。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Slf4j
@ApiVersion("26.10.09")
@RestController
@RequestMapping("/feature-flag")
@Validated
@Tag(name = "特性开关管理", description = "特性开关 CRUD 管理")
public class FeatureFlagAdminController {

  /** 内存存储 — flagKey → VO（后续可替换为数据库持久化） */
  private static final ConcurrentHashMap<String, FeatureFlagVO> STORE = new ConcurrentHashMap<>();

  /** 自增 ID 种子 */
  private static final AtomicLong ID_SEED = new AtomicLong(1);

  /** 日期时间格式 */
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /**
   * 分页查询特性开关列表。
   *
   * @param query 分页 + 筛选参数
   * @return 分页特性开关列表
   */
  @Operation(summary = "分页查询特性开关")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_CONFIG_VIEW)
  @GetMapping("/page")
  public YdszResponse<List<FeatureFlagVO>> page(FeatureFlagPageQuery query) {
    List<FeatureFlagVO> all = STORE.values().stream()
        .filter(vo -> query.getFlagKey() == null || vo.getFlagKey().contains(query.getFlagKey()))
        .filter(vo -> query.getFlagName() == null || vo.getFlagName().contains(query.getFlagName()))
        .filter(vo -> query.getFlagType() == null || query.getFlagType().equals(vo.getFlagType()))
        .filter(vo -> query.getStatus() == null || query.getStatus().equals(vo.getStatus()))
        .sorted(Comparator.comparing(FeatureFlagVO::getCreatedAt, Comparator.reverseOrder()))
        .collect(Collectors.toList());

    // 内存分页
    int pageNum = (query.getPageNum() != null && query.getPageNum() > 0) ? query.getPageNum() : 1;
    int pageSize = (query.getPageSize() != null && query.getPageSize() > 0 && query.getPageSize() <= 100)
        ? query.getPageSize() : 20;
    int fromIndex = (pageNum - 1) * pageSize;
    int toIndex = Math.min(fromIndex + pageSize, all.size());
    List<FeatureFlagVO> subList = fromIndex < all.size() ? all.subList(fromIndex, toIndex) : List.of();

    log.info("[FeatureFlagAdmin] page query: total={}, page={}/{}", all.size(), pageNum, pageSize);
    return PageResponse.success((long) all.size(), (long) pageNum, (long) pageSize, subList);
  }

  /**
   * 创建特性开关。
   *
   * @param dto 创建请求体
   * @return 创建后的开关 ID
   */
  @Idempotent(key = "ydsz:system:FeatureFlagAdmin:create", ttlSeconds = 5)
  @Audit(
      module = "特性开关",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建开关: ' + #dto.flagKey")
  @Operation(summary = "创建特性开关")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_CONFIG_EDIT)
  @PostMapping
  public YdszResponse<String> create(@Valid @RequestBody FeatureFlagDTO dto) {
    // 校验 key 唯一性
    boolean exists = STORE.values().stream()
        .anyMatch(vo -> vo.getFlagKey().equals(dto.getFlagKey()));
    if (exists) {
      return YdszResponse.error("B90001", "开关键已存在: " + dto.getFlagKey());
    }

    String id = IdGenerator.nextIdStr();
    FeatureFlagVO vo = new FeatureFlagVO();
    vo.setId(id);
    vo.setFlagKey(dto.getFlagKey());
    vo.setFlagName(dto.getFlagName());
    vo.setFlagType(dto.getFlagType());
    vo.setDefaultValue(dto.getDefaultValue());
    vo.setCurrentValue(dto.getCurrentValue());
    vo.setDescription(dto.getDescription());
    vo.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
    String now = LocalDateTime.now().format(DATE_FMT);
    vo.setCreatedAt(now);
    vo.setUpdatedAt(now);
    STORE.put(id, vo);
    log.info("[FeatureFlagAdmin] created: key={}, id={}", dto.getFlagKey(), id);
    return YdszResponse.success(id);
  }

  /**
   * 更新特性开关。
   *
   * @param dto 更新请求体（含 id）
   * @return 是否更新成功
   */
  @Idempotent(key = "ydsz:system:FeatureFlagAdmin:update", ttlSeconds = 3)
  @Audit(
      module = "特性开关",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新开关: ' + #dto.id")
  @Operation(summary = "更新特性开关")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_CONFIG_EDIT)
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody FeatureFlagDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      return YdszResponse.error("B90001", "更新时 id 不能为空");
    }
    FeatureFlagVO vo = STORE.get(dto.getId());
    if (vo == null) {
      return YdszResponse.error("B90001", "特性开关不存在: " + dto.getId());
    }
    // 校验 key 唯一性（排除自身）
    boolean keyChanged = !vo.getFlagKey().equals(dto.getFlagKey());
    if (keyChanged && STORE.values().stream().anyMatch(v -> v.getFlagKey().equals(dto.getFlagKey()))) {
      return YdszResponse.error("B90001", "开关键已存在: " + dto.getFlagKey());
    }
    vo.setFlagKey(dto.getFlagKey());
    vo.setFlagName(dto.getFlagName());
    vo.setFlagType(dto.getFlagType());
    vo.setDefaultValue(dto.getDefaultValue());
    vo.setCurrentValue(dto.getCurrentValue());
    vo.setDescription(dto.getDescription());
    vo.setStatus(dto.getStatus() != null ? dto.getStatus() : vo.getStatus());
    vo.setUpdatedAt(LocalDateTime.now().format(DATE_FMT));
    STORE.put(dto.getId(), vo);
    log.info("[FeatureFlagAdmin] updated: key={}, id={}", dto.getFlagKey(), dto.getId());
    return YdszResponse.success(Boolean.TRUE);
  }

  /**
   * 删除特性开关。
   *
   * @param id 开关 ID
   * @return 是否删除成功
   */
  @Audit(
      module = "特性开关",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除开关: ' + #id")
  @Operation(summary = "删除特性开关")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_CONFIG_EDIT)
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> delete(@PathVariable String id) {
    FeatureFlagVO removed = STORE.remove(id);
    if (removed == null) {
      return YdszResponse.error("B90001", "特性开关不存在: " + id);
    }
    log.info("[FeatureFlagAdmin] deleted: key={}, id={}", removed.getFlagKey(), id);
    return YdszResponse.success(Boolean.TRUE);
  }
}
