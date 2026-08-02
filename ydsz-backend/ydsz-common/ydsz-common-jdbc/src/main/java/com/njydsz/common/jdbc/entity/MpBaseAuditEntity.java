package com.njydsz.common.jdbc.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.domain.entity.BaseAuditEntity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MyBatis-Plus 增强版审计基础实体
 * 
 * <p>继承 ydsz-common-domain 的 {@link BaseAuditEntity}，在审计字段上添加 MyBatis-Plus 字段映射与自动填充注解。
 * 
 * <p><b>业务模块应直接依赖 ydsz-common-jdbc 并使用此类</b>，而非 ydsz-common-domain 的 BaseAuditEntity。
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
@EqualsAndHashCode(callSuper = false)
public class MpBaseAuditEntity<T extends Serializable> extends BaseAuditEntity<T> {

    private static final long serialVersionUID = 1L;

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

}
