package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 规则集安装结果视图对象（VO）。
 *
 * <p>用于前端展示一次规则集（知识包）安装的整体结果， 包含成功/失败计数及失败的规则编码，便于定位安装异常。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class InstallResultVO {

  /** 规则集编码 */
  private String packCode;

  /** 安装的目标版本号 */
  private String version;

  /** 待安装规则总数 */
  private int total;

  /** 安装成功数量 */
  private int success;

  /** 安装失败数量 */
  private int failed;

  /** 安装失败的规则编码列表（便于逐一排查） */
  private List<String> failedCodes;
}
