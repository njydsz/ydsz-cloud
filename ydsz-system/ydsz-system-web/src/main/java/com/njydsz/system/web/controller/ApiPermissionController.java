package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.vo.ApiPermissionVO;
import com.njydsz.system.server.service.ApiPermissionService;


/**
 * 接口权限管理 Controller
 *
 * <p>提供接口权限的查询、启用/禁用、删除以及手动触发扫描等管理功能。
 * 接口权限元数据由启动时自动扫描 {@code @AuthApiPermission} 注解注册到 DB，
 * 权限校验仍走 Redis（兼容现有体系），DB 仅作为「接口注册中心」展示哪些接口存在。
 *
 * <p><b>接口路径：</b>{@code /api/permission/api}
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.system.server.service.ApiPermissionService 接口权限业务逻辑
 */
@ApiVersion("26.09.01")
@Tag(name = "接口权限管理", description = "接口权限自动注册查看/同步管理")
@Slf4j
@RestController
@RequestMapping("/api/permission/api")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:permission:api-list")
public class ApiPermissionController {

  private final ApiPermissionService apiPermissionService;

  // ============================== 查询端点 ==============================

  /**
   * 分页查询接口权限
   *
   * <p>支持按 apiCode / apiName / controllerClass 模糊匹配、status 精确匹配过滤。
   *
   * @param query 分页查询条件
   * @return 分页结果
   */
  @Operation(summary = "分页查询")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<ApiPermissionVO>>> page(ApiPermissionQuery query) {
    return YdszResponse.success(apiPermissionService.page(query));
  }

  /**
   * 按 ID 查询接口权限
   *
   * @param id 主键 ID
   * @return 接口权限详情；不存在时返回 null
   */
  @Operation(summary = "按 ID 查询")
  @GetMapping("/{id}")
  public YdszResponse<ApiPermissionVO> getById(@PathVariable String id) {
    return YdszResponse.success(apiPermissionService.getById(id));
  }

  // ============================== 管理操作端点 ==============================

  /**
   * 重新触发接口扫描
   *
   * <p>重新扫描所有 Controller 上的 {@code @AuthApiPermission} 注解，将新发现的接口权限注册到 DB。
   * 已有的权限码不会覆盖。
   *
   * @return 新注册的接口数量
   */
  @Operation(summary = "触发重新扫描", description = "重新扫描 @AuthApiPermission 注解注册接口权限")
  @PostMapping("/scan")
  @AuthApiPermission(apiCodes = "sys:permission:api-scan")
  public YdszResponse<Integer> triggerScan() {
    return YdszResponse.success(apiPermissionService.scanAndRegister());
  }

  /**
   * 启用接口权限
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  @Operation(summary = "启用接口权限")
  @PostMapping("/{id}/enable")
  @AuthApiPermission(apiCodes = "sys:permission:api-edit")
  public YdszResponse<Boolean> enable(@PathVariable String id) {
    return YdszResponse.success(apiPermissionService.enable(id));
  }

  /**
   * 禁用接口权限
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  @Operation(summary = "禁用接口权限")
  @PostMapping("/{id}/disable")
  @AuthApiPermission(apiCodes = "sys:permission:api-edit")
  public YdszResponse<Boolean> disable(@PathVariable String id) {
    return YdszResponse.success(apiPermissionService.disable(id));
  }

  /**
   * 删除接口权限（逻辑删除）
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  @Operation(summary = "删除接口权限", description = "逻辑删除（设置 deleted=1）")
  @DeleteMapping("/{id}")
  @AuthApiPermission(apiCodes = "sys:permission:api-delete")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(apiPermissionService.removeById(id));
  }
}
