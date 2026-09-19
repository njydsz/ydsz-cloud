package com.njydsz.common.auth.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 权限目录清单。
 *
 * <p>汇总系统中全部已注册的菜单/接口权限码及其元数据，用于：
 *
 * <ul>
 *   <li>前端权限配置界面渲染</li>
 *   <li>对接三方 SDK 时获取权限空间清单</li>
 *   <li>运维审计当前系统的权限暴露面</li>
 * </ul>
 *
 * <p>由 {@link com.njydsz.common.auth.endpoint.PermissionCatalogService} 启动时扫描构建。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class PermissionCatalog implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 目录条目集合（不可变）。 */
  private final List<PermissionEntry> entries;

  /** 构建时间戳（毫秒）。 */
  private final long buildTimestamp;

  /**
   * 构造权限目录。
   *
   * @param entries 条目集合
   * @param buildTimestamp 构建时间戳（毫秒）
   */
  public PermissionCatalog(List<PermissionEntry> entries, long buildTimestamp) {
    this.entries = entries != null ? Collections.unmodifiableList(entries) : Collections.emptyList();
    this.buildTimestamp = buildTimestamp;
  }

  /**
   * 获取目录条目集合。
   *
   * @return 不可变的权限条目列表
   */
  public List<PermissionEntry> getEntries() {
    return entries;
  }

  /**
   * 获取目录构建时间戳。
   *
   * @return 构建时间戳（毫秒）
   */
  public long getBuildTimestamp() {
    return buildTimestamp;
  }

  /**
   * 获取目录条目总数。
   *
   * @return 条目总数
   */
  public int size() {
    return entries.size();
  }

  /**
   * 权限目录条目。
   *
   * <p>描述一个权限码的完整元数据。
   */
  public static class PermissionEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限码（如 {@code sys:user:list} 或 {@code /api/user/create}）。 */
    private final String code;

    /** 权限类型。 */
    private final PermissionType type;

    /** 所属 Controller 类名（简单名）。 */
    private final String controller;

    /** 方法名。 */
    private final String method;

    /**
     * 权限模式（ANY = 满足任一即可 / ALL = 必须全部满足）。
     */
    private final String mode;

    /**
     * 构造权限条目。
     *
     * @param code 权限码
     * @param type 权限类型
     * @param controller Controller 简单名
     * @param method 方法名
     * @param mode 权限模式（ANY/ALL）
     */
    public PermissionEntry(
        String code, PermissionType type, String controller, String method, String mode) {
      this.code = code;
      this.type = type;
      this.controller = controller;
      this.method = method;
      this.mode = mode;
    }

    public String getCode() {
      return code;
    }

    public PermissionType getType() {
      return type;
    }

    public String getController() {
      return controller;
    }

    public String getMethod() {
      return method;
    }

    public String getMode() {
      return mode;
    }
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
}
