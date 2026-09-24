package com.njydsz.literule.domain.entity;

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
 * <p>对应 {@code ydsz_rule_pack_install} 表，记录每次安装规则包（{@link RulePack}）的操作流水。
 * 状态机：INSTALLING（安装中）→ INSTALLED（安装成功）/ FAILED（安装失败），
 * 以及 UNINSTALLING（卸载中）→ UNINSTALLED（已卸载）。
 *
 * <p>由规则引擎的安装服务在下载规则包并批量写入规则定义表时驱动状态流转，
 * 安装失败时 {@code errorMessage} 记录异常堆栈以便排查。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @Data 与 JPA 继承共用，父类字段泛型擦除
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_pack_install")
public class RulePackInstall extends MpBaseEntity<String> {

  /** 安装操作人 ID */
  private String installedBy;

  /** 安装时间 */
  private LocalDateTime installedAt;

  /** 安装状态：INSTALLING / INSTALLED / FAILED / UNINSTALLING / UNINSTALLED */
  private String status;

  /** 失败原因（status=FAILED 时记录异常信息） */
  private String errorMessage;
}
