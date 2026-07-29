package com.njydsz.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.common.json.annotation.YdszJsonFormat;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 审计字段基础实体
 *
 * <p>继承自 {@link BaseIdEntity}，额外包含创建人、创建时间、更新人、更新时间等审计字段
 * 这些字段由 MyBatis-Plus 的自动填充功能管理，对业务代码透明。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>审计字段对业务代码透明，由框架自动维护</li>
 *   <li>采用 LocalDateTime 作为时间类型，支持时区转换</li>
 *   <li>使用 {@code @YdszJsonFormat} 注解支持 JSON 序列化时的格式控制</li>
 * </ul>
 *
 * <p><b>审计字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th><th>填充时机</th></tr>
 *   <tr><td>createdBy</td><td>String</td><td>创建人ID</td><td>INSERT</td></tr>
 *   <tr><td>createdAt</td><td>LocalDateTime</td><td>创建时间</td><td>INSERT</td></tr>
 *   <tr><td>updatedBy</td><td>String</td><td>更新人ID</td><td>INSERT/UPDATE</td></tr>
 *   <tr><td>updatedAt</td><td>LocalDateTime</td><td>更新时间</td><td>INSERT/UPDATE</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * public class User extends BaseAuditEntity<Long> {
 *     private String username;
 *     private String email;
 *     private String phone;
 * }
 * }</pre>
 *
 * <p><b>数据库表结构：</b>
 * <pre>{@code
 * CREATE TABLE sys_user (
 *     id BIGINT PRIMARY KEY,
 *     username VARCHAR(50),
 *     email VARCHAR(100),
 *     phone VARCHAR(20),
 *     created_by VARCHAR(64),
 *     created_at DATETIME,
 *     updated_by VARCHAR(64),
 *     updated_at DATETIME
 * );
 * }</pre>
 *
 * <p><b>重构规划：</b>当前审计字段（createdBy/createdAt/updatedBy/updatedAt）以继承方式内联于此类中。
 * 未来计划提取为 {@code AuditInfo} 值对象，通过 {@code @Embedded} 组合方式替代继承。
 * 这将降低继承链深度，提高实体类的组合灵活性，并使审计信息可独立复用。
 * 迁移路径：BaseAuditEntity -> BaseIdEntity + @Embedded AuditInfo
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see BaseIdEntity
 * @see BaseEntity
 * @see RootEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseAuditEntity<T extends Serializable> extends BaseIdEntity<T> implements Auditable {

    private static final long serialVersionUID = 1L;

    /**
     * 创建人ID
     *
     * <p>记录创建该记录的用户ID，通常从 SecurityContext 安全上下文中获取。
     * 框架在 INSERT 操作时自动填充此字段
     *
     * <p><b>字段映射：</b> created_by -> createdBy
     */
    private String createdBy;

    /**
     * 创建时间
     *
     * <p>记录创建该记录的时间戳。
     * 框架在 INSERT 操作时自动填充此字段
     * JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     *
     * <p><b>字段映射：</b> created_at -> createdAt
     */
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     *
     * <p>记录最后一次更新该记录的用户ID。
     * 框架在 INSERT/UPDATE 操作时自动填充此字段
     *
     * <p><b>字段映射：</b> updated_by -> updatedBy
     */
    private String updatedBy;

    /**
     * 更新时间
     *
     * <p>记录最后一次更新该记录的时间戳。
     * 框架在 INSERT/UPDATE 操作时自动填充此字段
     * JSON 序列化时格式化为 "yyyy-MM-dd HH:mm:ss"。
     *
     * <p><b>字段映射：</b> updated_at -> updatedAt
     */
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 判断是否为新建实体
     *
     * <p>根据 createdAt 是否为 null 判断是否为新建实体。
     * 用于业务逻辑判断，通常用于审计日志等场景。
     *
     * @return 新建实体返回true，否则返回false
     */
    public boolean isFresh() {
        return this.createdAt == null;
    }

    /**
     * 获取创建来源标识
     *
     * <p>用于记录数据创建来源，如：API、BATCH、IMPORT 等。
     * 默认为空，子类可根据业务需要扩展。
     *
     * @return 创建来源标识
     */
    public String getCreateSource() {
        return null;
    }
}