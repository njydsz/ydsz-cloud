package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 文件级 ACL 权限实体
 *
 * <p>独立于系统级 RBAC，实现文件/文件夹级别的细粒度权限控制。 支持按用户或角色授予读/写/删除/分享/下载等权限。
 *
 * <p><b>权限矩阵：</b>
 *
 * <ul>
 *   <li>read - 查看文件/列出目录
 *   <li>write - 上传/编辑/重命名
 *   <li>delete - 删除文件/文件夹
 *   <li>share - 创建分享链接
 *   <li>download - 下载文件
 * </ul>
 *
 * <p><b>继承传播：</b>文件夹的 ACL 自动继承给子节点，除非子节点有显式 ACL 覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_file_acl")
public class FileAcl extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件节点ID */
  private String fileNodeId;

  /** 授权对象类型：user / role / group / tenant */
  private String granteeType;

  /** 授权对象ID（用户ID / 角色ID / 组ID / 租户ID） */
  private String granteeId;

  /** 权限位掩码（read=1, write=2, delete=4, share=8, download=16） */
  private Integer permissionMask;

  /** 是否继承自父目录 */
  private Boolean inherited;

  /** 是否为所有者（所有者拥有全部权限） */
  private Boolean owner;

  /** 权限位常量：读取 */
  public static final int PERM_READ = 1;

  /** 权限位常量：写入 */
  public static final int PERM_WRITE = 2;

  /** 权限位常量：删除 */
  public static final int PERM_DELETE = 4;

  /** 权限位常量：分享 */
  public static final int PERM_SHARE = 8;

  /** 权限位常量：下载 */
  public static final int PERM_DOWNLOAD = 16;

  /** 全部权限 */
  public static final int PERM_ALL =
      PERM_READ | PERM_WRITE | PERM_DELETE | PERM_SHARE | PERM_DOWNLOAD;

  /**
   * 检查是否拥有指定权限。
   *
   * @param permission 目标权限位
   * @return 权限掩码包含目标权限位时返回 true
   */
  public boolean hasPermission(int permission) {
    return permissionMask != null && (permissionMask & permission) == permission;
  }
}
