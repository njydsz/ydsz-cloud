package com.njydsz.literule.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则包安装记录实体。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_pack_install")
public class RulePackInstallDO extends MpBaseEntity<String> {

  /** 安装操作人 ID */
  private String installedBy;

  /** 安装时间 */
  private LocalDateTime installedAt;

  /** 安装状态：INSTALLING / INSTALLED / FAILED / UNINSTALLING / UNINSTALLED */
  private String status;

  /** 失败原因（status=FAILED 时记录异常信息） */
  private String errorMessage;
}
