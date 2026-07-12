package com.njydsz.pmis.common.audit.enums;

/**
 * 审计操作行为枚举
 * <p>
 * 定义审计日志中所有受支持的操作行为分类。编码值（{@code code}）用于数据库存储和索引，
 * 描述（{@code description}）用于界面展示，二者解耦以保证历史数据的稳定性。
 * </p>
 *
 * <p><b>编码规范：</b>1-99 为通用操作，99 为兜底的 {@link #OTHER}。
 * 新增业务专属操作建议从 100 起，避免与通用枚举冲突。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public enum AuditAction {

    /** 新增（创建实体） */
    CREATE(1, "新增"),

    /** 修改（更新实体字段） */
    UPDATE(2, "修改"),

    /** 删除（逻辑删除或物理删除） */
    DELETE(3, "删除"),

    /** 查询（数据检索） */
    QUERY(4, "查询"),

    /** 导入（批量录入） */
    IMPORT(5, "导入"),

    /** 导出（批量下载数据） */
    EXPORT(6, "导出"),

    /** 上传（文件/资源上传） */
    UPLOAD(7, "上传"),

    /** 下载（文件/资源下载） */
    DOWNLOAD(8, "下载"),

    /** 登录（用户登录系统） */
    LOGIN(9, "登录"),

    /** 登出（用户退出系统） */
    LOGOUT(10, "登出"),

    /** 授权（赋予权限/角色） */
    GRANT(11, "授权"),

    /** 取消授权（撤销权限/角色） */
    REVOKE(12, "取消授权"),

    /** 启用（将状态切换为可用） */
    ENABLE(13, "启用"),

    /** 禁用（将状态切换为不可用） */
    DISABLE(14, "禁用"),

    /** 审核（流程审批通过） */
    APPROVE(15, "审核"),

    /** 驳回（流程审批拒绝） */
    REJECT(16, "驳回"),

    /** 重置（密码/状态等重置） */
    RESET(17, "重置"),

    /** 锁定（用户/账户锁定） */
    LOCK(18, "锁定"),

    /** 解锁（解除用户/账户锁定） */
    UNLOCK(19, "解锁"),

    /** 备份（数据备份） */
    BACKUP(20, "备份"),

    /** 恢复（从备份恢复数据） */
    RESTORE(21, "恢复"),

    /** 同步（数据同步） */
    SYNC(22, "同步"),

    /** 清理（数据清理/归档） */
    CLEAN(23, "清理"),

    /** 其他（未归类操作，作为默认兜底） */
    OTHER(99, "其他");

    /**
     * 操作编码（数据库持久化值，1-99 通用，100+ 自定义）
     */
    private final int code;

    /**
     * 操作描述（界面展示文案，i18n 友好）
     */
    private final String description;

    AuditAction(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取操作编码
     *
     * @return 编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取操作描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取审计操作枚举
     *
     * @param code 编码
     * @return 审计操作；未匹配时返回 {@link #OTHER} 兜底
     */
    public static AuditAction fromCode(int code) {
        for (AuditAction action : values()) {
            if (action.code == code) {
                return action;
            }
        }
        return OTHER;
    }

    /**
     * 判断是否为写操作（会产生数据变更的操作）
     * <p>写操作通常需要更严格的审计要求（必填、不可降级），读操作可选择性记录。
     *
     * @return 是写操作返回 true
     */
    public boolean isWriteOperation() {
        return this == CREATE || this == UPDATE || this == DELETE
                || this == IMPORT || this == UPLOAD
                || this == GRANT || this == REVOKE
                || this == ENABLE || this == DISABLE
                || this == APPROVE || this == REJECT
                || this == RESET || this == LOCK
                || this == UNLOCK || this == BACKUP
                || this == RESTORE || this == SYNC
                || this == CLEAN;
    }
}
