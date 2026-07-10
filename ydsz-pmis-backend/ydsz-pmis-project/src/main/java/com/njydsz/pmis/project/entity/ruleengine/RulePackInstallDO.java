package com.njydsz.pmis.project.entity.ruleengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则集安装历史实体（P2-14）。
 *
 * <p>对应 {@code pmis_rule_pack_install} 表，记录规则集在租户环境下的安装/卸载历史。
 * 每次规则集安装（或卸载）都会插入一条记录，用于审计追溯和回滚定位。
 *
 * <p>安装状态流转：INSTALLING → INSTALLED / FAILED；卸载状态：UNINSTALLING → UNINSTALLED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-14)
 */
@Data
@TableName("pmis_rule_pack_install")
public class RulePackInstallDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法字符串） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 规则集编码（关联 {@code pmis_rule_pack.pack_code}） */
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
