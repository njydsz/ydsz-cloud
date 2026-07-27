package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 语言配置 DO 实体。
 *
 * <p>对应数据表 ydsz_language，
 * 继承 {@code MpBaseEntity} 提供公共审计字段（id/创建时间/更新时间等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_language")
public class LanguageDO extends MpBaseEntity<String> {

    private String languageCode;
    private String languageName;
    private Integer isDefault;
    private Integer sortOrder;
    private String status;
}
