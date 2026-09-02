package com.njydsz.nextwiki.server.security;

/**
 * 知识库空间权限码常量（S4-P3-03）。
 *
 * <p>定义空间 RBAC 权限码，用于前端按钮级权限控制 + 后端接口权限校验。
 *
 * <p><b>权限码格式：</b>{@code NEXTWIKI_SPACE_<ACTION>}
 *
 * <p><b>角色-权限映射：</b>
 *
 * <pre>
 *   owner:  SPACE_CREATE, SPACE_UPDATE, SPACE_DELETE, SPACE_ARCHIVE,
 *           MEMBER_INVITE, MEMBER_REMOVE, MEMBER_ROLE_UPDATE,
 *           NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
 *           SETTINGS_VIEW, SETTINGS_EDIT
 *
 *   admin:  MEMBER_INVITE, MEMBER_REMOVE,
 *           NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
 *           SETTINGS_VIEW
 *
 *   editor: NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
 *           SETTINGS_VIEW
 *
 *   viewer: SETTINGS_VIEW
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SpacePermissionCodes {

  /** 私有构造方法防止实例化 */
  private SpacePermissionCodes() {
  }

  // ==================== 空间管理权限 ====================

  /** 创建空间 */
  public static final String SPACE_CREATE = "NEXTWIKI_SPACE_CREATE";

  /** 更新空间信息 */
  public static final String SPACE_UPDATE = "NEXTWIKI_SPACE_UPDATE";

  /** 删除空间 */
  public static final String SPACE_DELETE = "NEXTWIKI_SPACE_DELETE";

  /** 归档空间 */
  public static final String SPACE_ARCHIVE = "NEXTWIKI_SPACE_ARCHIVE";

  // ==================== 成员管理权限 ====================

  /** 邀请成员 */
  public static final String MEMBER_INVITE = "NEXTWIKI_SPACE_MEMBER_INVITE";

  /** 移除成员 */
  public static final String MEMBER_REMOVE = "NEXTWIKI_SPACE_MEMBER_REMOVE";

  /** 修改成员角色 */
  public static final String MEMBER_ROLE_UPDATE = "NEXTWIKI_SPACE_MEMBER_ROLE_UPDATE";

  /** 查看成员列表 */
  public static final String MEMBER_LIST = "NEXTWIKI_SPACE_MEMBER_LIST";

  // ==================== 节点操作权限 ====================

  /** 创建节点（文件/文件夹） */
  public static final String NODE_CREATE = "NEXTWIKI_SPACE_NODE_CREATE";

  /** 更新节点 */
  public static final String NODE_UPDATE = "NEXTWIKI_SPACE_NODE_UPDATE";

  /** 删除节点 */
  public static final String NODE_DELETE = "NEXTWIKI_SPACE_NODE_DELETE";

  /** 移动节点 */
  public static final String NODE_MOVE = "NEXTWIKI_SPACE_NODE_MOVE";

  // ==================== 设置权限 ====================

  /** 查看空间设置 */
  public static final String SETTINGS_VIEW = "NEXTWIKI_SPACE_SETTINGS_VIEW";

  /** 编辑空间设置 */
  public static final String SETTINGS_EDIT = "NEXTWIKI_SPACE_SETTINGS_EDIT";

  /**
   * 根据角色获取权限码列表。
   *
   * @param role 角色名称（owner/admin/editor/viewer）
   * @return 该角色拥有的权限码数组
   */
  public static String[] getPermissionsByRole(String role) {
    if (role == null) {
      return new String[0];
    }
    switch (role) {
      case "owner":
        return new String[]{
            SPACE_CREATE, SPACE_UPDATE, SPACE_DELETE, SPACE_ARCHIVE,
            MEMBER_INVITE, MEMBER_REMOVE, MEMBER_ROLE_UPDATE, MEMBER_LIST,
            NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
            SETTINGS_VIEW, SETTINGS_EDIT
        };
      case "admin":
        return new String[]{
            MEMBER_INVITE, MEMBER_REMOVE, MEMBER_LIST,
            NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
            SETTINGS_VIEW
        };
      case "editor":
        return new String[]{
            NODE_CREATE, NODE_UPDATE, NODE_DELETE, NODE_MOVE,
            SETTINGS_VIEW
        };
      case "viewer":
        return new String[]{
            SETTINGS_VIEW
        };
      default:
        return new String[0];
    }
  }

  /**
   * 检查角色是否拥有指定权限。
   *
   * @param role 角色名称
   * @param permissionCode 权限码
   * @return 若角色拥有该权限则返回 true
   */
  public static boolean hasPermission(String role, String permissionCode) {
    for (String perm : getPermissionsByRole(role)) {
      if (perm.equals(permissionCode)) {
        return true;
      }
    }
    return false;
  }
}
