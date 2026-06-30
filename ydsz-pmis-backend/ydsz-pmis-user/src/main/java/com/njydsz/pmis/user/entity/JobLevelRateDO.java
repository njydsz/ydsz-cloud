package com.njydsz.pmis.user.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String levelCode;

    /** 对外人天（元/天） */
    private BigDecimal externalDaily;

    /** 对内人天（元/天） */
    private BigDecimal internalDaily;

    private BigDecimal baseSalary;

    private BigDecimal socialCompany;
    private BigDecimal socialPersonal;
    private BigDecimal fundCompany;
    private BigDecimal fundPersonal;
    private BigDecimal takeHome;
    private BigDecimal totalCost;

    /** 可计费利用率目标 (0-1) */
    private BigDecimal billableTarget;

    private LocalDate effectiveDate;
    private LocalDate expireDate;

    private Integer version;

    private String description;
}
