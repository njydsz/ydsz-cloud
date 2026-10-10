package com.njydsz.system.server.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.system.server.service.ApiPermissionService;


/**
 * 接口权限启动扫描器
 *
 * <p>{@code @Order(100)}  CommandLineRunner，Spring 启动完成后自动触发接口权限扫描注册。
 * 委托 {@link ApiPermissionService#scanAndRegister()} 完成实际的扫描与注册逻辑。
 *
 * <p><b>设计决策：</b>扫描器仅作为启动入口，不包含业务逻辑，保持单一职责。
 *
 * <p><b>异常处理：</b>扫描注册失败不会阻止应用启动，仅记录错误日志。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see ApiPermissionService#scanAndRegister() 扫描注册 Service 方法
 */
@Slf4j
@Order(100)
@Component
public class AppPermissionScanRunner implements CommandLineRunner {

  private final ApiPermissionService apiPermissionService;

  /**
   * 构造注入。
   *
   * @param apiPermissionService 接口权限 Service
   */
  public AppPermissionScanRunner(ApiPermissionService apiPermissionService) {
    this.apiPermissionService = apiPermissionService;
  }

  /**
   * 启动扫描入口（CommandLineRunner 回调）。
   *
   * <p>启动时自动扫描所有 {@code @AuthApiPermission} 注解接口并注册到 DB。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(String... args) {
    log.info(I18n.message("system.api.scan.start"));
    try {
      int registered = apiPermissionService.scanAndRegister();
      log.info(I18n.message("system.api.scan.done", new Object[]{registered}));
    } catch (Exception e) {
      // 扫描注册失败不应阻止应用启动
      log.error(I18n.message("system.api.scan.fail", new Object[]{e.getMessage()}), e);
    }
  }
}
