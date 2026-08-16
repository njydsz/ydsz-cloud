package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 规则集版本差异视图对象（VO）。
 *
 * <p>用于前端展示同一规则集在两个版本之间的规则变更情况， 包含新增、移除与修改的规则编码列表，支撑版本对比与升级预览。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PackDiffVO {

  /** 规则集编码 */
  private String packCode;

  /** 对比的源版本号（旧版本） */
  private String fromVersion;

  /** 对比的目标版本号（新版本） */
  private String toVersion;

  /** 相对源版本新增的规则编码列表 */
  private List<String> added;

  /** 相对源版本移除的规则编码列表 */
  private List<String> removed;

  /** 相对源版本发生内容变更的规则编码列表 */
  private List<String> changed;
}
