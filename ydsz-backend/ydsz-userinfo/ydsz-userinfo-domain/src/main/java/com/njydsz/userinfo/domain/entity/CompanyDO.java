package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 公司信息 DO 实体。
 *
 * <p>对应数据表 ydsz_company，
 * 继承 {@code MpBaseEntity} 提供公共审计字段（id/创建时间/更新时间等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_company")
public class CompanyDO extends MpBaseEntity<String> {

    private String companyName;
    private String companyCode;
    private String parentId;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String status;
}
