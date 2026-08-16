package com.njydsz.system.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 系统配置版本实体
 *
 * <p>对应数据库表 {@code ydsz_config_version}，记录配置项（{@code configKey}）的变更历史快照。 每次配置发生变更（save / updateById
 * / removeById）时，{@link com.njydsz.system.server.service.impl.ConfigServiceImpl} 会自动创建一条版本快照。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code resourceKey}：配置键（{@code ydsz_config.config_key}），版本管理的最小粒度
 *   <li>{@code configGroup}：配置分组（冗余存储，便于按分组查询版本历史）
 *   <li>{@code version}：版本号字符串（如 {@code "v1734567890123"}，时间戳）
 *   <li>{@code changeLog}：变更说明（如「修改超时时间: 30s → 60s」）
 *   <li>{@code snapshotJson}：变更前配置项 JSON 快照，支撑回滚
 *   <li>{@code effectiveDate}：生效时间（版本被创建时间）
 * </ul>
 *
 * <p><b>与 DictVersion 的区别：</b>
 *
 * <ul>
 *   <li>DictVersion 按 {@code typeCode} 分组（一个版本覆盖整个字典类型）
 *   <li>ConfigVersion 按 {@code configKey} 单键粒度（每个配置键独立版本链）
 * </ul>
 *
 * <p><b>索引设计：</b>索引 {@code idx_resource_key_version}（{@code resource_key}, {@code version}）， 加速按
 * resourceKey 查询版本历史与按 version 回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.ConfigVersionService 配置版本业务逻辑
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_config_version")
public class ConfigVersion extends MpBaseEntity<String> {

  /** 配置键（{@code ydsz_config.config_key}），版本管理的最小粒度 */
  private String resourceKey;

  /** 配置分组（冗余存储，便于按分组查询版本历史） */
  private String configGroup;

  /** 版本号字符串（默认 {@code "v" + currentTimeMillis}） */
  private String version;

  /** 变更说明（如「修改超时时间: 30s → 60s」） */
  private String changeLog;

  /** 变更前配置项 JSON 快照（用于回滚） */
  private String snapshotJson;

  /** 生效时间（版本被创建时间） */
  private LocalDateTime effectiveDate;
}
