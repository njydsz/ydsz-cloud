package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置 VO（视图对象）
 *
 * <p>对应 {@code ydsz_config} 表的展示视图，是「系统配置中心」列表 / 详情接口的响应载体。
 * 由 {@link com.njydsz.system.infra.converter.SystemConverter} 从 {@link
 * com.njydsz.system.infra.entity.Config} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code configValue} — 配置值，序列化时按 {@code valueType} 转换： {@code STRING} 原样输出；{@code NUMBER /
 *       BOOLEAN / JSON} 解析为对应类型
 *   <li>{@code defaultValue} — 默认值（{@code configValue} 为空时回退）
 *   <li>{@code isPublic} — 是否对前端公开：{@code 1} 公开 / {@code 0} 仅后端； 前端「公开配置」接口仅返回 {@code isPublic=1}
 *       的项
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>配置中心列表 / 详情 / 编辑回显
 *   <li>业务参数动态下发（{@code ydsz.workflow.sla-default-hours} 等）
 *   <li>前端「公开配置」接口返回值（开关、限流阈值、特性开关等）
 * </ul>
 *
 * <p><b>缓存策略：</b>读取通过 ydsz-common-cache 进程内本地缓存（键 {@code value:{tenantId}:{configKey}}）；
 * 写入时 {@code @CacheEvict} 精准失效；TTL 通过 {@code ydsz.cache.caches.system:config} 配置（默认 5 分钟）。
 *
 * <p><b>注意：</b>本类为视图对象，不包含输入校验逻辑。输入校验由 {@link
 * com.njydsz.system.domain.dto.ConfigDTO} 负责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.Config 系统配置实体
 * @see com.njydsz.system.domain.dto.ConfigDTO 配置输入 DTO
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ConfigVO {

  private String id;

  private String configGroup;

  private String configKey;

  private String configValue;

  private String valueType;

  private String defaultValue;

  private String description;

  private Integer isPublic;

  private Integer sortOrder;

  private String status;
}
