package com.njydsz.common.jdbc.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.njydsz.common.json.annotation.JsonFormat;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MyBatis-Plus 增强版审计基础实体
 *
 * <p>包含审计字段（创建人/时间、更新人/时间），由 MyBatis-Plus 自动填充
 * （{@link FieldFill#INSERT} / {@link FieldFill#INSERT_UPDATE}）。
 *
 * <p><b>v1.4.0</b>：不再继承 common-domain 的 BaseAuditEntity，字段内联自洽，
 * 业务模块实体仅依赖 ydsz-common-jdbc 一个模块。
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
public class MpBaseAuditEntity<T extends Serializable> extends MpBaseIdEntity<T> {

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
