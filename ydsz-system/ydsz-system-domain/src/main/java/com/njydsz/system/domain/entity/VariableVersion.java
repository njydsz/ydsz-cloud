package com.njydsz.system.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 系统变量版本实体
 *
 * <p>对应数据库表 {@code ydsz_variable_version}，记录变量（{@code variableKey}）的变更历史快照。 每次变量发生变更（save /
 * updateById / removeById）时，{@link com.njydsz.system.server.service.impl.VariableServiceImpl}
 * 会自动创建一条版本快照。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code resourceKey}：变量键（{@code ydsz_variable.variable_key}），版本管理的最小粒度
 *   <li>{@code version}：版本号字符串（如 {@code "v1734567890123"}，时间戳）
 *   <li>{@code changeLog}：变更说明（如「调整会计年度: 2024 → 2025」）
 *   <li>{@code snapshotJson}：变更前变量 JSON 快照，支撑回滚
 *   <li>{@code effectiveDate}：生效时间（版本被创建时间）
 * </ul>
 *
 * <p><b>索引设计：</b>索引 {@code idx_resource_key_version}（{@code resource_key}, {@code version}）， 加速按
 * resourceKey 查询版本历史与按 version 回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.VariableVersionService 变量版本业务逻辑
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_variable_version")
public class VariableVersion extends MpBaseEntity<String> {

  /** 变量键（{@code ydsz_variable.variable_key}），版本管理的最小粒度 */
  private String resourceKey;

  /** 版本号字符串（默认 {@code "v" + currentTimeMillis}） */
  private String version;

  /** 变更说明（如「调整会计年度: 2024 → 2025」） */
  private String changeLog;

  /** 变更前变量 JSON 快照（用于回滚） */
  private String snapshotJson;

  /** 生效时间（版本被创建时间） */
  private LocalDateTime effectiveDate;
}
