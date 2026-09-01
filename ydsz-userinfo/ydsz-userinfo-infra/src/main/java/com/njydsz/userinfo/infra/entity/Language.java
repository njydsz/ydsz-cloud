package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 语言配置实体
 *
 * <p>对应数据库表 {@code ydsz_acct_user_language}，存储系统支持的语言种类及默认语言标识。 用于前端 i18n 国际化与后端消息文案回退链。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code languageCode}：语言编码（ISO 639-1，如 {@code zh-CN} / {@code en-US}）
 *   <li>{@code isDefault}：是否默认语言（{@code 1=是}，系统全局仅允许 1 个默认语言）
 *   <li>{@code sortOrder}：语言切换器中的展示顺序
 * </ul>
 *
 * <p><b>默认语言唯一性：</b>系统全局仅允许 1 个默认语言，由 Service 层事务保证。 修改默认语言时，应在事务内同时取消旧默认、设置新默认。
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>前端 i18n 加载：从 {@code /api/v1/Language/list} 获取所有启用语言，构造语言切换器
 *   <li>后端消息文案：通过 {@code LocaleContextHolder} 获取当前语言，匹配 {@code ydsz_i18n_message} 表
 *   <li>浏览器语言探测：根据 {@code Accept-Language} 头选择最匹配语言
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_language_code}（{@code language_code}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.web.controller.LanguageController 语言 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_acct_user_language")
public class Language extends MpBaseEntity<String> {

  /**
   * 语言编码（ISO 639-1 + ISO 3166-1 区域码）。
   *
   * <p>常见取值：{@code zh-CN}（简体中文）/ {@code en-US}（美式英语）/ {@code ja-JP}（日语）/ {@code zh-TW}（繁体中文）。
   */
  private String languageCode;

  /** 语言名称（前端展示，如「简体中文」「English」） */
  private String languageName;

  /**
   * 是否默认语言。
   *
   * <p>{@code 1=是}、{@code 0=否}。系统全局仅允许 1 个默认语言。
   */
  private Integer isDefault;

  /** 排序序号（升序，决定语言切换器展示顺序） */
  private Integer sortOrder;

  /**
   * 启用状态（{@code "ENABLED"} / {@code "DISABLED"}）
   *
   * <p>禁用后，前端语言切换器隐藏该选项，但已登录用户的语言偏好保留。
   */
  private String status;
}
