package com.njydsz.common.auth.metrics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 权限目录注册表（全局单例）。
 *
 * <p>收集当前应用声明的全部权限码元数据，统一暴露给前端权限配置界面、运维审计或三方 SDK。
 *
 * <p>注册方式（由业务模块在配置类中调用）：
 *
 * <pre>{@code
 * &#64;Configuration
 * public class PermissionRegistrationConfig {
 *   &#64;Bean
 *   public Object registerPermissions(PermissionCatalogRegistry registry) {
 *     registry.registerMenu("sys:user:list", "用户列表", "UserController");
 *     registry.registerMenu("sys:user:add", "新增用户", "UserController");
 *     registry.registerApi("sys:user:create", "创建用户接口", "UserController#create");
 *     return new Object();
 *   }
 * }
 * }</pre>
 *
 * <p>通过 {@link com.njydsz.common.auth.model.PermissionCatalog} 可导出完整的不可变目录快照。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Component
public class PermissionCatalogRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionCatalogRegistry.class);

  /** 权限条目存储（线程安全）。 */
  private final ConcurrentHashMap<String, PermissionEntry> entries = new ConcurrentHashMap<>(128);

  /**
   * 注册菜单权限。
   *
   * @param code 权限码（如 {@code sys:user:list}）
   * @param description 权限描述（用于前端展示）
   * @param source 来源标识（通常为 Controller 简单名）
   */
  public void registerMenu(String code, String description, String source) {
    register(code, PermissionType.MENU, description, source);
  }

  /**
   * 注册按钮权限。
   *
   * @param code 按钮权限码（如 {@code sys:user:export}）
   * @param description 按钮描述
   * @param source 来源标识
   */
  public void registerButton(String code, String description, String source) {
    register(code, PermissionType.BUTTON, description, source);
  }

  /**
   * 注册接口权限。
   *
   * @param code 接口权限码（如 {@code /api/user/create} 或 {@code user:create}）
   * @param description 接口描述
   * @param source 来源标识（通常为 Controller#method）
   */
  public void registerApi(String code, String description, String source) {
    register(code, PermissionType.API, description, source);
  }

  /**
   * 注册权限条目（通用方法）。
   *
   * <p>重复注册同一权限码时以后来 {@code description}/{@code source} 覆盖。
   *
   * @param code 权限码（非空、非空白）
   * @param type 权限类型
   * @param description 描述文案
   * @param source 来源标识（Controller 或 Controller#method）
   */
  public void register(String code, PermissionType type, String description, String source) {
    if (code == null || code.isBlank()) {
      return;
    }
    String key = code.trim();
    entries.put(key, new PermissionEntry(key, type, description, source));
  }

  /**
   * 导出不可变的权限目录快照。
   *
   * @return {@link PermissionCatalog} 实例（不可变）
   */
  public PermissionCatalog snapshot() {
    List<PermissionEntry> list = new ArrayList<>(entries.values());
    return new PermissionCatalog(Collections.unmodifiableList(list), System.currentTimeMillis());
  }

  /**
   * 获取当前已注册权限数量。
   *
   * @return 权限条目数
   */
  public int size() {
    return entries.size();
  }

  /**
   * 清空注册表。
   */
  public void clear() {
    entries.clear();
  }

  /**
   * 获取所有已注册权限码集合。
   *
   * @return 不可变权限码集合
   */
  public Set<String> registeredCodes() {
    return Collections.unmodifiableSet(entries.keySet());
  }

  /** 权限类型枚举。 */
  public enum PermissionType {
    /** 菜单权限 */
    MENU,
    /** 按钮权限 */
    BUTTON,
    /** 接口权限 */
    API
  }

  /**
   * 权限条目值对象。
   *
   * <p>实现 {@link Serializable} 便于通过缓存或消息序列化传播目录快照。
   */
  public static class PermissionEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限码 */
    private final String code;

    /** 权限类型 */
    private final PermissionType type;

    /** 描述文案 */
    private final String description;

    /** 来源标识 */
    private final String source;

    /**
     * 构造权限条目。
     *
     * @param code 权限码
     * @param type 权限类型
     * @param description 描述
     * @param source 来源标识
     */
    public PermissionEntry(
        String code, PermissionType type, String description, String source) {
      this.code = code;
      this.type = type;
      this.description = description;
      this.source = source;
    }

    public String getCode() {
      return code;
    }

    public PermissionType getType() {
      return type;
    }

    public String getDescription() {
      return description;
    }

    public String getSource() {
      return source;
    }
  }
}
