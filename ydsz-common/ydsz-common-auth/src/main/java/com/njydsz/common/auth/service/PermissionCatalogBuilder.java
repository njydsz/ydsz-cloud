package com.njydsz.common.auth.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.annotation.AuthMenuPermission;
import com.njydsz.common.auth.model.PermissionCatalog;
import com.njydsz.common.auth.model.PermissionCatalog.PermissionEntry;
import com.njydsz.common.auth.model.PermissionCatalog.PermissionType;

/**
 * 权限目录构建器。
 *
 * <p>启动时通过 Spring {@link RequestMappingHandlerMapping} 扫描所有 Controller 端点方法， 提取 {@link AuthMenuPermission} 和
 * {@link AuthApiPermission} 注解中的权限码元数据，构建 {@link PermissionCatalog} 供 {@link
 * com.njydsz.common.auth.endpoint.PermissionCatalogEndpoint} 暴露。
 *
 * <p>扫描策略：
 *
 * <ul>
 *   <li>遍历所有已注册的 HandlerMethod（覆盖所有 MVC 端点）</li>
 *   <li>检查方法级和类级 {@link AuthMenuPermission} / {@link AuthApiPermission} 注解</li>
 *   <li>提取 permissionCodes / apiCodes 并转换为目录条目</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Component
public class PermissionCatalogBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionCatalogBuilder.class);

  private final ApplicationContext applicationContext;

  /** 构建后的权限目录（不可变，一次构建全程复用）。 */
  private PermissionCatalog catalog;

  public PermissionCatalogBuilder(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * 启动时触发扫描并构建目录。
   *
   * <p>扫描失败时记录错误但不阻塞启动（降级为目录不可用）。
   */
  @PostConstruct
  public void build() {
    try {
      List<PermissionEntry> entries = new ArrayList<>(64);
      // 获取所有请求映射（含方法级元数据）
      Map<RequestMappingInfo, HandlerMethod> handlerMethods =
          applicationContext.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
      for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
        HandlerMethod handlerMethod = entry.getValue();
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();
        // 菜单/按钮权限
        AuthMenuPermission methodMenuAnn =
            AnnotationUtils.findAnnotation(method, AuthMenuPermission.class);
        AuthMenuPermission classMenuAnn =
            AnnotationUtils.findAnnotation(controllerClass, AuthMenuPermission.class);
        if (methodMenuAnn != null) {
          addMenuEntries(entries, methodMenuAnn, controllerClass.getSimpleName(), method.getName());
        }
        if (classMenuAnn != null && classMenuAnn != methodMenuAnn) {
          addMenuEntries(entries, classMenuAnn, controllerClass.getSimpleName(), method.getName());
        }
        // 接口权限
        AuthApiPermission methodApiAnn =
            AnnotationUtils.findAnnotation(method, AuthApiPermission.class);
        AuthApiPermission classApiAnn =
            AnnotationUtils.findAnnotation(controllerClass, AuthApiPermission.class);
        if (methodApiAnn != null) {
          addApiEntries(entries, methodApiAnn, controllerClass.getSimpleName(), method.getName());
        }
        if (classApiAnn != null && classApiAnn != methodApiAnn) {
          addApiEntries(entries, classApiAnn, controllerClass.getSimpleName(), method.getName());
        }
      }
      this.catalog = new PermissionCatalog(entries, System.currentTimeMillis());
      LOG.info("[PermissionCatalogBuilder] 权限目录构建完成: {} 个条目", entries.size());
    } catch (Exception e) {
      LOG.error("[PermissionCatalogBuilder] 权限目录构建失败，不影响启动: {}", e.getMessage(), e);
    }
  }

  /**
   * 获取构建完成的权限目录。
   *
   * @return 目录实例；构建失败时返回空目录
   */
  public PermissionCatalog getCatalog() {
    return catalog != null
        ? catalog
        : new PermissionCatalog(List.of(), System.currentTimeMillis());
  }

  /**
   * 触发重新扫描（可在权限注册变更后调用）。
   */
  public void rebuild() {
    build();
  }

  /** 添加菜单/按钮权限条目（去重）。 */
  private void addMenuEntries(
      List<PermissionEntry> entries,
      AuthMenuPermission ann,
      String controller,
      String methodName) {
    PermissionType type =
        ann.type() == AuthMenuPermission.PermissionType.BUTTON
            ? PermissionType.BUTTON
            : PermissionType.MENU;
    String mode = ann.mode() != null ? ann.mode().name() : "ANY";
    Set<String> added = new java.util.HashSet<>(entries.size());
    for (String code : ann.permissionCodes()) {
      if (code != null && !code.isBlank() && added.add(code.trim())) {
        entries.add(new PermissionEntry(code.trim(), type, controller, methodName, mode));
      }
    }
  }

  /** 添加接口权限条目（去重）。 */
  private void addApiEntries(
      List<PermissionEntry> entries,
      AuthApiPermission ann,
      String controller,
      String methodName) {
    String mode = ann.mode() != null ? ann.mode().name() : "ANY";
    Set<String> added = new java.util.HashSet<>(entries.size());
    for (String code : ann.apiCodes()) {
      if (code != null && !code.isBlank() && added.add(code.trim())) {
        entries.add(new PermissionEntry(code.trim(), PermissionType.API, controller, methodName, mode));
      }
    }
  }
}
