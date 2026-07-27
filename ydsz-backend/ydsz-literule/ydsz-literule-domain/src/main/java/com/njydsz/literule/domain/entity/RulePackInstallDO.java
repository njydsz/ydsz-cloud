package com.njydsz.literule.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

    /** 安装操作人 ID */
    private String installedBy;

    /** 安装时间 */
    private LocalDateTime installedAt;

    /** 安装状态：INSTALLING / INSTALLED / FAILED / UNINSTALLING / UNINSTALLED */
    private String status;

    /** 失败原因（status=FAILED 时记录异常信息） */
    private String errorMessage;
}
