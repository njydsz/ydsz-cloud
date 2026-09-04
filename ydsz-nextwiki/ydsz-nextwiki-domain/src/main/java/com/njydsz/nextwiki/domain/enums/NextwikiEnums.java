package com.njydsz.nextwiki.domain.enums;


/**
 * 网盘知识库枚举集合
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class NextwikiEnums {

  private NextwikiEnums() {}

  /** 节点类型：区分目录与文件节点 */
  public enum NodeType {
    /** 目录节点，可包含子节点 */
    FOLDER,
    /** 文件节点，叶子节点，关联底层存储对象 */
    FILE
  }

  /** 分享类型：决定访客对分享内容的操作权限级别 */
  public enum ShareType {
    /** 仅查看（只读预览，不可下载/编辑） */
    VIEW,
    /** 可查看并下载 */
    DOWNLOAD,
    /** 可查看、下载并编辑 */
    EDIT
  }

  /** 分享状态 */
  public enum ShareStatus implements BaseStatusEnum<ShareStatus> {
    /** 分享生效中 */
    ACTIVE("active"),
    /** 分享已过期（到达过期时间） */
    EXPIRED("expired"),
    /** 分享已撤销（被所有者主动取消） */
    REVOKED("revoked");

    private final String code;

    ShareStatus(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已过期/已撤销），不可再迁移
     */
    @Override
    public boolean isTerminal() {
      return this == EXPIRED || this == REVOKED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    @Override
    public boolean canTransitTo(ShareStatus target) {
      if (target == null) {
        return false;
      }
      if (this == target) {
        return true;
      }
      if (this.isTerminal()) {
        return false;
      }
      return switch (this) {
        case ACTIVE -> target == EXPIRED || target == REVOKED;
        default -> false;
      };
    }

    /**
     * 根据编码反查枚举（大小写不敏感）
     *
     * @param code 状态编码
     * @return 枚举值；未匹配返回 null
     */
    public static ShareStatus fromCode(String code) {
      if (code == null) {
        return null;
      }
      for (ShareStatus s : values()) {
        if (s.code.equalsIgnoreCase(code)) {
          return s;
        }
      }
      return null;
    }
  }

  /** ACL 授权对象类型：权限可授予到用户/角色/组/租户维度 */
  public enum GranteeType {
    /** 授予到具体用户 */
    USER,
    /** 授予到角色（该角色下所有用户继承权限） */
    ROLE,
    /** 授予到用户组 */
    GROUP,
    /** 授予到租户（租户内全部用户继承权限） */
    TENANT
  }

  /** 版本变更类型：记录每次版本产生的原因 */
  public enum ChangeType {
    /** 首次创建文件产生的版本 */
    CREATE,
    /** 文件内容/元数据更新产生的版本 */
    UPDATE,
    /** 回滚历史版本生成的版本 */
    ROLLBACK
  }

  /** 回收站状态 */
  public enum TrashStatus implements BaseStatusEnum<TrashStatus> {
    /** 在回收站中（可还原/彻底删除） */
    IN_TRASH("in_trash"),
    /** 已还原到原位置 */
    RESTORED("restored"),
    /** 已彻底清除（不可恢复） */
    PURGED("purged");

    private final String code;

    TrashStatus(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    /**
     * 判断是否为终态
     *
     * @return true 表示当前状态为终态（已恢复/已清理），不可再迁移
     */
    @Override
    public boolean isTerminal() {
      return this == RESTORED || this == PURGED;
    }

    /**
     * 校验状态迁移合法性
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态迁移到目标状态
     */
    @Override
    public boolean canTransitTo(TrashStatus target) {
      if (target == null) {
        return false;
      }
      if (this == target) {
        return true;
      }
      if (this.isTerminal()) {
        return false;
      }
      return switch (this) {
        case IN_TRASH -> target == RESTORED || target == PURGED;
        default -> false;
      };
    }

    /**
     * 根据编码反查枚举（大小写不敏感）
     *
     * @param code 状态编码
     * @return 枚举值；未匹配返回 null
     */
    public static TrashStatus fromCode(String code) {
      if (code == null) {
        return null;
      }
      for (TrashStatus s : values()) {
        if (s.code.equalsIgnoreCase(code)) {
          return s;
        }
      }
      return null;
    }
  }

  /** 共享状态：文件节点自身的共享范围（区别于分享链接状态的 ShareStatus） */
  public enum ShareStatusField {
    /** 私有，仅自己可见 */
    PRIVATE,
    /** 已通过 ACL 分享给其他人 */
    SHARED,
    /** 完全公开，任何人可访问 */
    PUBLIC
  }

  /** 配额维度：配额按哪种主体维度进行统计与限制 */
  public enum QuotaScopeType {
    /** 按用户维度统计配额 */
    USER,
    /** 按租户维度统计配额 */
    TENANT,
    /** 按项目维度统计配额 */
    PROJECT
  }

  /** 标签类型：标签的来源与可编辑性 */
  public enum TagType {
    /** 用户手动创建 */
    MANUAL,
    /** 系统基于文档内容自动推荐 */
    AUTO,
    /** 系统预置标签，不可删除 */
    SYSTEM
  }

  /** 文件排序方式：目录/搜索结果列表的默认排序字段 */
  public enum SortBy {
    /** 按文件名称排序 */
    NAME,
    /** 按文件大小排序 */
    SIZE,
    /** 按创建时间排序 */
    CREATED_AT,
    /** 按更新时间排序 */
    UPDATED_AT
  }
}
