package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.vo.FrontendInitVO;
import com.njydsz.system.server.service.FrontendInitService;

/**
 * 前端初始化 Controller
 *
 * <p>提供前端启动时所需数据的聚合查询接口，减少前端初始化时的请求次数。
 *
 * <p>聚合返回：
 *
 * <ul>
 *   <li>公开配置（feature flag、UI 文案、限流阈值等前端可读参数）
 *   <li>常用字典数据（用户状态、性别、优先级等下拉框数据源）
 *   <li>系统版本号
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/system/init}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "前端初始化", description = "前端启动聚合数据接口")
@Slf4j
@RequestMapping("/api/v1/system/init")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:frontend:init")
public class FrontendInitController {

  private final FrontendInitService frontendInitService;

  /**
   * 获取前端初始化数据（含默认字典类型）
   *
   * <p>返回公开配置与默认字典类型（user_status、gender、priority、task_status）， 覆盖前端启动时最常用的下拉框数据源。
   *
   * @return 前端初始化聚合数据
   */
  @Operation(summary = "获取前端初始化数据", description = "聚合返回公开配置、默认字典、系统版本等前端启动数据")
  // P2-2: 公开接口添加限流保护（前端高频调用，防止恶意刷接口）
  @RateLimit(resource = "system.frontend.init", threshold = 100)
  @GetMapping
  public YdszResponse<FrontendInitVO> init() {
    return YdszResponse.success(frontendInitService.getInitData());
  }

  /**
   * 获取指定字典类型的初始化数据
   *
   * <p>按类型编码查询字典项，用于前端按需加载特定下拉框数据。
   *
   * @param dictTypes 字典类型编码列表（如 user_status,gender,priority）
   * @return 前端初始化聚合数据（含指定字典）
   */
  @Operation(
      summary = "获取指定字典的初始化数据",
      description = "按需指定字典类型，返回公开配置与指定字典数据")
  // P2-2: 公开接口添加限流保护
  @RateLimit(resource = "system.frontend.init.dicts", threshold = 100)
  @GetMapping("/dicts")
  public YdszResponse<FrontendInitVO> initWithDicts(
      @Parameter(description = "字典类型编码列表")
          @RequestParam("dictTypes") List<String> dictTypes) {
    return YdszResponse.success(frontendInitService.getInitDataWithDicts(dictTypes));
  }
}
