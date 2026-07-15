package com.njydsz.pmis.common.jdbc.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.njydsz.pmis.common.domain.entity.BaseIdEntity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MyBatis-Plus 增强版主键基础实体
 * 
 * <p>继承 ydsz-pmis-common-domain 的 {@link BaseIdEntity}，在主键字段上添加 {@link TableId} 注解，
 * 启用 MyBatis-Plus 的雪花算法自动生成 ID。
 * 
 * <p><b>业务模块应直接依赖 ydsz-pmis-common-jdbc 并使用此类</b>，而非 ydsz-pmis-common-domain 的 BaseIdEntity。
 * 
 * @param <T> 主键ID类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = false)
public class MpBaseIdEntity<T extends Serializable> extends BaseIdEntity<T> {

    private static final long serialVersionUID = 1L;

    /** 主键ID，使用雪花算法自动生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private T id;

}
