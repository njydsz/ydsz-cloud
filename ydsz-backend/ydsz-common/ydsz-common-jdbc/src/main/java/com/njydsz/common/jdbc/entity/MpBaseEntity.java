package com.njydsz.common.jdbc.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.njydsz.common.json.annotation.YdszJsonFormat;
import com.njydsz.common.json.annotation.JsonIgnore;
import com.njydsz.common.domain.entity.BaseEntity;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * MyBatis-Plus 增强版基础实体
 * 
 * <p>在 ydsz-common-domain 的 {@link BaseEntity} 基础上，添加 MyBatis-Plus 注解，包括：
 * <ul>
 *   <li>{@link TableId} — 主键 ID，使用雪花算法自动生成</li>
 *   <li>{@link TableField} + {@link FieldFill} — 审计字段自动填充（创建人/时间、更新人/时间）</li>
 *   <li>{@link TableField} — 乐观锁版本号字段映射（由自定义 OptimisticLockInterceptor 处理，不使用 @Version）</li>
 *   <li>{@link TableField} — 逻辑删除标识字段映射（由自定义 LogicalDeleteInterceptor 处理，不使用 @TableLogic）</li>
 *   <li>{@link TableField} — 状态标识字段映射</li>
 * </ul>
 * 
 * <p><b>业务模块应直接依赖 ydsz-common-jdbc 并使用此类</b>，而非 ydsz-common-domain 的 BaseEntity。
 * 
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class User extends MpBaseEntity {
 *     private String username;
 *     private String email;
 *     private String phone;
 * }
 * }</pre>
 * 
 * @param <T> 主键ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
public class MpBaseEntity<T extends Serializable> extends BaseEntity<T> {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID，使用雪花算法自动生成
     */
    @TableId(type = IdType.ASSIGN_ID)
    private T id;

    /**
     * 创建人ID
     * <p>框架在 INSERT 操作时自动填充。
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * 创建时间
     * <p>框架在 INSERT 操作时自动填充。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     * <p>框架在 INSERT/UPDATE 操作时自动填充。
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /**
     * 更新时间
     * <p>框架在 INSERT/UPDATE 操作时自动填充。
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @YdszJsonFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号
     * <p>每次更新时自动递增（+1），防止并发更新冲突。
     * <p>由自定义 {@code OptimisticLockInterceptor} 处理，不使用 {@code @Version} 注解，
     * 避免与自定义拦截器产生双重处理冲突。当未启用自定义拦截器时，
     * 可在业务实体上单独添加 {@code @Version} 注解使用 MP 内置能力。
     */
    @TableField("revision")
    @Builder.Default
    private Integer revision = 0;

    /**
     * 逻辑删除标识
     * <p>0=未删除，1=已删除。删除操作转为 UPDATE，查询自动追加 WHERE deleted = 0。
     * <p>由自定义 {@code LogicalDeleteInterceptor} 处理，不使用 {@code @TableLogic} 注解，
     * 避免与自定义拦截器产生双重处理冲突。当未启用自定义拦截器时，
     * 可在业务实体上单独添加 {@code @TableLogic} 注解使用 MP 内置能力。
     */
    @TableField("deleted")
    @JsonIgnore
    private Integer deleted;

    /**
     * 状态标识
     * <p>子类可按需覆盖为具体业务状态枚举值，默认值为空。
     */
    @TableField("status")
    private String status;

    /**
     * 租户 ID
     *
     * <p>多租户隔离字段，由 SQL 拦截器自动注入 WHERE 条件和 INSERT 填充。
     * <p>单租户模式下默认值 "1"，多租户模式由 TenantContextHolder 注入。
     * <p>业务 DO 无需再单独声明此字段。
     * <p>对外 API 不暴露租户 ID。
     *
     * <p><b>注意：</b>此字段始终存在于 MpBaseEntity 中。当未引入
     * {@code common-tenant} 模块或未启用多租户时，此字段被忽略
     * （DDL 默认值 '1'），不会影响业务逻辑。
     */
    @TableField("tenant_id")
    @JsonIgnore
    private String tenantId;

}
