package com.njydsz.system.infra.entity;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;




/**
 * 统一实体版本实体
 *
 * <p>对应数据库表 {@code ydsz_sys_entity_version}，为 Config/Dict/Variable 提供统一的变更历史快照管理。
 * 通过 {@link #resourceType} 字段区分不同业务类型，替代原有的三套独立版本表。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code resourceType}：资源类型（CONFIG/DICT/VARIABLE），区分业务域
 *   <li>{@code resourceKey}：资源唯一标识（configKey/typeCode/variableKey）
 *   <li>{@code resourceGroup}：资源分组（configGroup，DICT/VARIABLE 类型为 null）
 *   <li>{@code version}：版本号字符串
 *   <li>{@code changeLog}：变更说明
 *   <li>{@code snapshotJson}：变更前 JSON 快照，支撑回滚
 *   <li>{@code effectiveDate}：生效时间
 * </ul>
 *
 * <p><b>索引设计：</b>复合索引 {@code idx_resource_type_key_version}（{@code resource_type}, {@code resource_key},
 * {@code version}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_sys_entity_version")
public class EntityVersion extends MpBaseEntity<String> {

  /** 资源类型：CONFIG/DICT/VARIABLE */
  private String resourceType;

  /** 资源唯一标识（configKey / typeCode / variableKey） */
  private String resourceKey;

  /** 资源分组（仅 CONFIG 类型使用，其他为 null） */
  private String resourceGroup;

  /** 版本号字符串 */
  private String version;

  /** 变更说明 */
  private String changeLog;

  /** 变更前 JSON 快照（用于回滚） */
  private String snapshotJson;

  /** 生效时间 */
  private LocalDateTime effectiveDate;
}
