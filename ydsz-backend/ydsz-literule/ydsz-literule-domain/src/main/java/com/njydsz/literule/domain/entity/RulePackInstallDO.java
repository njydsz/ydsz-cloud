package com.njydsz.literule.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则集安装历史实体（P2-14）。
 *
 * <p>对应 {@code ydsz_rule_pack_install} 表，记录规则集在租户环境下的安装/卸载历史。
 * 每次规则集安装（或卸载）都会插入一条记录，用于审计追溯和回滚定位。
 *
 * <p>安装状态流转：INSTALLING → INSTALLED / FAILED；卸载状态：UNINSTALLING → UNINSTALLED。
 *
 * @author ydsz-team
 * @since 1.0.0 (P2-14)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_pack_install")
public class RulePackInstallDO extends MpBaseEntity<String> {

    /** 规则集编码（关联 {@code ydsz_rule_pack.pack_code}） */
    private String packCode;

    /** 规则集版本号（如 v1.0.0） */
    private String packVersion;

    /** 租户 ID */
    private String tenantId;

    /** 安装操作人 ID */
    private String installedBy;

    /** 安装时间 */
    private LocalDateTime installedAt;

    /** 安装状态：INSTALLING / INSTALLED / FAILED / UNINSTALLING / UNINSTALLED */
    private String status;

    /** 失败原因（status=FAILED 时记录异常信息） */
    private String errorMessage;
}
