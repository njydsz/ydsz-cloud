package com.njydsz.nextwiki.domain.enums;

/**
 * 网盘知识库枚举集合
 *
 * @author ydsz-team
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
        ACTIVE("active"),
        EXPIRED("expired"),
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
        public boolean isTerminal() {
            return this == EXPIRED || this == REVOKED;
        }

        /**
         * 校验状态迁移合法性
         *
         * @param target 目标状态
         * @return true 表示允许从当前状态迁移到目标状态
         */
        public boolean canTransitTo(ShareStatus target) {
            if (target == null) return false;
            if (this == target) return true;
            if (this.isTerminal()) return false;
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
            if (code == null) return null;
            for (ShareStatus s : values()) {
                if (s.code.equalsIgnoreCase(code)) return s;
            }
            return null;
        }
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
        IN_TRASH("in_trash"),
        RESTORED("restored"),
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
        public boolean isTerminal() {
            return this == RESTORED || this == PURGED;
        }

        /**
         * 校验状态迁移合法性
         *
         * @param target 目标状态
         * @return true 表示允许从当前状态迁移到目标状态
         */
        public boolean canTransitTo(TrashStatus target) {
            if (target == null) return false;
            if (this == target) return true;
            if (this.isTerminal()) return false;
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
            if (code == null) return null;
            for (TrashStatus s : values()) {
                if (s.code.equalsIgnoreCase(code)) return s;
            }
            return null;
        }
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
