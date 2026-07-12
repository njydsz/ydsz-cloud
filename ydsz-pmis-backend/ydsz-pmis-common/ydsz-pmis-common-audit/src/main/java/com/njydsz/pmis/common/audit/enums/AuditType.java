package com.njydsz.pmis.common.audit.enums;

/**
 * 审计类型枚举
 * <p>
 * 区分审计日志所属的审计域（如操作、登录、数据、权限等），用于：
 * <ul>
 *   <li>审计日志的查询与分类统计</li>
 *   <li>不同审计域的合规要求（保留周期、脱敏策略）</li>
 *   <li>独立分表/独立存储路由</li>
 * </ul>
 *
 * <p><b>编码规范：</b>1-99 为内置通用类型，99 为自定义兜底。
 * 业务方可定义 100+ 的扩展类型并存入同一张表（不建议）或单独的物理表。</p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public enum AuditType {

    /** 操作审计：增删改查等通用业务操作（最常见） */
    OPERATION(1, "操作审计"),

    /** 登录审计：登录、登出、刷新令牌等安全相关操作 */
    LOGIN(2, "登录审计"),

    /** 数据审计：导入、导出、批量变更等敏感数据操作 */
    DATA(3, "数据审计"),

    /** 权限审计：角色授权、权限分配、权限回收等 */
    PERMISSION(4, "权限审计"),

    /** 配置审计：系统参数、字典、菜单等配置变更 */
    CONFIG(5, "配置审计"),

    /** 文件审计：文件上传、下载、删除、分享等 */
    FILE(6, "文件审计"),

    /** 接口审计：对外/对内接口调用（含第三方网关） */
    API(7, "接口审计"),

    /** 系统审计：定时任务、缓存清理等系统级操作 */
    SYSTEM(8, "系统审计"),

    /** 自定义审计：业务方自定义类型，建议分配 100+ 编码 */
    CUSTOM(99, "自定义审计");

    /**
     * 类型编码（数据库持久化值；1-99 内置通用，99 自定义兜底）
     */
    private final int code;

    /**
     * 类型描述（界面展示文案）
     */
    private final String description;

    AuditType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取类型编码
     *
     * @return 编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取类型描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取审计类型枚举
     *
     * @param code 编码
     * @return 审计类型；未匹配时返回 {@link #OPERATION} 兜底
     */
    public static AuditType fromCode(int code) {
        for (AuditType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return OPERATION;
    }
}
