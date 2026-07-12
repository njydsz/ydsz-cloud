package com.njydsz.pmis.common.jdbc.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.*;
import com.njydsz.pmis.common.entity.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 增强版基础实体
 * 
 * <p>在 pmis-common 的 {@link BaseEntity} 基础上，添加 MyBatis-Plus 注解，包括：
 * <ul>
 *   <li>{@link TableId} — 主键 ID，使用雪花算法自动生成</li>
 *   <li>{@link TableField} + {@link FieldFill} — 审计字段自动填充（创建人/时间、更新人/时间）</li>
 *   <li>{@link Version} — 乐观锁版本号</li>
 *   <li>{@link TableLogic} — 逻辑删除标识</li>
 *   <li>{@link TableField} — 状态标识字段映射</li>
 * </ul>
 * 
 * <p><b>业务模块应直接依赖 pmis-common 并使用此类</b>，而非 pmis-common 的 BaseEntity。
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
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
public class MpBaseEntity<T extends java.io.Serializable> extends BaseEntity<T> {

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号
     * <p>每次更新时自动递增（+1），防止并发更新冲突。
     */
    @TableField("revision")
    @Version
    @Builder.Default
    private Integer revision = 0;

    /**
     * 逻辑删除标识
     * <p>0=未删除，1=已删除。删除操作转为 UPDATE，查询自动追加 WHERE deleted = 0。
     */
    @TableField("deleted")
    @TableLogic
    @JsonIgnore
    private Integer deleted;

    /**
     * 状态标识
     * <p>0=禁用，1=正常/启用。
     */
    @TableField("status")
    private Integer status;

}
