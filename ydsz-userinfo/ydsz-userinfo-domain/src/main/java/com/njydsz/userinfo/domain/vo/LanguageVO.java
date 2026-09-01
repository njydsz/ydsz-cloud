package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 语言配置视图对象。
 *
 * <p>管理多语言支持的启用的语言列表，供用户选择语言偏好和前端 i18n 加载。
 * 不包含 deleted、createdBy 等内部维护字段。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code languageCode} — 语言编码（如 zh-CN、en-US、ja-JP）</li>
 *   <li>{@code languageName} — 语言名称（如"简体中文"、"English"）</li>
 *   <li>{@code isDefault} — 是否默认语言（1=是、0=否，用户未指定语言偏好时回退）</li>
 *   <li>{@code sortOrder} — 排序序号（越小越靠前）</li>
 *   <li>{@code status} — 状态（ENABLE-启用、DISABLE-禁用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class LanguageVO {

  /** 语言唯一标识 */
  private String id;

  /** 语言编码，如 zh-CN、en-US */
  private String languageCode;

  /** 语言名称，如 简体中文 */
  private String languageName;

  /** 是否默认语言：1-是、0-否 */
  private Integer isDefault;

  /** 排序序号 */
  private Integer sortOrder;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;
}
