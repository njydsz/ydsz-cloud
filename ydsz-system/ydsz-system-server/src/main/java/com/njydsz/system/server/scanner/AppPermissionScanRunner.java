package com.njydsz.system.server.scanner;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.tenant.SystemTenantContextRunner;
import com.njydsz.system.api.client.ApiPermissionRegistrationClient;
import com.njydsz.system.domain.dto.ApiPermissionRegistrationRequest;
import com.njydsz.system.domain.vo.ApiPermissionVO;
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

  private static final String PLATFORM_SERVICE_NAME = "ydsz-system";

  private final ApiPermissionService apiPermissionService;
  private final ApiPermissionRegistrationClient registrationClient;

  /**
   * 构造注入。
   *
   * @param apiPermissionService 接口权限 Service
   * @param registrationClient API 权限注册 Feign 客户端
   */
  public AppPermissionScanRunner(
      ApiPermissionService apiPermissionService,
      ApiPermissionRegistrationClient registrationClient) {
    this.apiPermissionService = apiPermissionService;
    this.registrationClient = registrationClient;
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
      // CommandLineRunner 线程无请求租户上下文，需注入系统租户，否则权限表写入被租户隔离拦截器拒绝
      int registered =
          SystemTenantContextRunner.call(() -> apiPermissionService.scanAndRegister());
      syncApiPermissionsToUserinfo();
      log.info(I18n.message("system.api.scan.done", new Object[]{registered}));
    } catch (Exception e) {
      // 扫描注册失败不应阻止应用启动
      log.error(I18n.message("system.api.scan.fail", new Object[]{e.getMessage()}), e);
    }
  }

  /**
   * 将 system 扫描到的启用 API 权限同步到 userinfo RBAC 权限表。
   *
   * <p>userinfo 不可用时仅记录告警，不阻断 system 启动；失败项由后续扫描重试。
   */
  private void syncApiPermissionsToUserinfo() {
    try {
      List<ApiPermissionVO> permissions =
          SystemTenantContextRunner.call(apiPermissionService::listAll);
      if (permissions == null || permissions.isEmpty()) {
        return;
      }
      int synced = 0;
      for (ApiPermissionVO permission : permissions) {
        if (!"ENABLED".equals(permission.getStatus())) {
          continue;
        }
        ApiPermissionRegistrationRequest request = new ApiPermissionRegistrationRequest();
        request.setApiCode(permission.getApiCode());
        request.setApiName(permission.getApiName());
        request.setServiceName(PLATFORM_SERVICE_NAME);
        registrationClient.register(request);
        synced++;
      }
      log.info(I18n.message("system.api.permission.sync.done", new Object[]{synced}));
    } catch (Exception e) {
      log.warn(I18n.message("system.api.permission.sync.fail", new Object[]{e.getMessage()}), e);
    }
  }
}
