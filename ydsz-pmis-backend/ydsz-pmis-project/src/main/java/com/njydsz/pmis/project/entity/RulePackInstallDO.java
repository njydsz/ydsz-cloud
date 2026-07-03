package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则集安装历史 DO（P2-14）
 */
@Data
@TableName("pmis_rule_pack_install")
public class RulePackInstallDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packCode;
    private String packVersion;
    private Long tenantId;
    private String installedBy;
    private LocalDateTime installedAt;
    private String status;
    private String errorMessage;
}
