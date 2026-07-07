package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 职级费率实体（双费率）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_level_rate")
public class JobLevelRateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 职级编码 */
    private String levelCode;

    /** 对外人天（元/天） */
    private BigDecimal externalDaily;

    /** 对内人天（元/天） */
    private BigDecimal internalDaily;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 社保公司部分 */
    private BigDecimal socialCompany;
    /** 社保个人部分 */
    private BigDecimal socialPersonal;
    /** 公积金公司部分 */
    private BigDecimal fundCompany;
    /** 公积金个人部分 */
    private BigDecimal fundPersonal;
    /** 税后到手 */
    private BigDecimal takeHome;
    /** 用工总成本 */
    private BigDecimal totalCost;

    /** 可计费利用率目标 (0-1) */
    private BigDecimal billableTarget;

    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expireDate;

    /** 版本号 */
    private Integer version;

    /** 描述 */
    private String description;
}
