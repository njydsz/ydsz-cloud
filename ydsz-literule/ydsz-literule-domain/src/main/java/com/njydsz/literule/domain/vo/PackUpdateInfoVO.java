package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则集更新信息视图对象（VO）。
 *
 * <p>用于前端展示已安装规则集是否有新版本可升级， 包含已安装版本与最新版本对比、是否有更新及安装时间等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PackUpdateInfoVO {

  /** 规则集编码 */
  private String packCode;

  /** 规则集名称（展示用） */
  private String packName;

  /** 当前已安装版本号 */
  private String installedVersion;

  /** 最新可用版本号 */
  private String latestVersion;

  /** 是否存在可更新版本（true=有新版可升级） */
  private boolean hasUpdate;

  /** 安装时间 */
  private LocalDateTime installedAt;

  /** 所属行业 */
  private String industry;

  /** 规则集描述 */
  private String description;
}
