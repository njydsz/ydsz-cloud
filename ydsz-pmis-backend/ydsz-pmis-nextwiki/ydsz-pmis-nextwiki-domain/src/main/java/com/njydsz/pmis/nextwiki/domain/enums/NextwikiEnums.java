package com.njydsz.pmis.nextwiki.domain.enums;

/**
 * 网盘知识库枚举集合
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class NextwikiEnums {

    private NextwikiEnums() {
    }

    /** 节点类型 */
    public enum NodeType {
        FOLDER,
        FILE
    }

    /** 分享类型 */
    public enum ShareType {
        VIEW,
        DOWNLOAD,
        EDIT
    }

    /** 分享状态 */
    public enum ShareStatus {
        ACTIVE,
        EXPIRED,
        REVOKED
    }

    /** ACL 授权对象类型 */
    public enum GranteeType {
        USER,
        ROLE,
        GROUP,
        TENANT
    }

    /** 版本变更类型 */
    public enum ChangeType {
        CREATE,
        UPDATE,
        ROLLBACK
    }

    /** 回收站状态 */
    public enum TrashStatus {
        IN_TRASH,
        RESTORED,
        PURGED
    }

    /** 共享状态 */
    public enum ShareStatusField {
        PRIVATE,
        SHARED,
        PUBLIC
    }

    /** 配额维度 */
    public enum QuotaScopeType {
        USER,
        TENANT,
        PROJECT
    }

    /** 标签类型 */
    public enum TagType {
        MANUAL,
        AUTO,
        SYSTEM
    }

    /** 文件排序方式 */
    public enum SortBy {
        NAME,
        SIZE,
        CREATED_AT,
        UPDATED_AT
    }
}
