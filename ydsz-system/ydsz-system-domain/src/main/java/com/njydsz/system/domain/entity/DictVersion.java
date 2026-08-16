package com.njydsz.system.domain.entity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.baomidou.mybatisplus.annotation.TableName;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 字典版本实体
 *
 * <p>对应数据库表 {@code ydsz_dict_version}，记录字典变更历史快照。 每次字典项发生变更（save / updateById / removeById）时，{@link
 * com.njydsz.system.server.service.DictItemServiceImpl} 会自动创建一条版本快照（{@code
 * DictVersionService.createVersion}）。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code typeCode}：所属字典类型编码（{@code ydsz_dict_type.type_code}）
 *   <li>{@code version}：版本号字符串（如 {@code "v1734567890123"}，时间戳）
 *   <li>{@code changeLog}：变更说明（如「新增字典项: order_paid」）
 *   <li>{@code snapshotJson}：变更前全量字典项 JSON 快照，支撑回滚
 *   <li>{@code effectiveDate}：生效时间（版本被创建时间）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>查询某个 typeCode 的所有版本历史，按 {@code effectiveDate} 倒序
 *   <li>对比两个版本之间的差异（diff）
 *   <li>回滚到指定版本（{@code DictVersionService.rollbackTo}，使用 snapshotJson 重建字典）
 * </ul>
 *
 * <p><b>索引设计：</b>索引 {@code idx_type_code_version}（{@code type_code}, {@code version}）， 加速按 typeCode
 * 查询版本历史与按 version 回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.DictVersionService 字典版本业务逻辑
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_version")
public class DictVersion extends MpBaseEntity<String> {

  /** 所属字典类型编码（{@code ydsz_dict_type.type_code}） */
  private String typeCode;

  /** 版本号字符串（默认 {@code "v" + currentTimeMillis}） */
  private String version;

  /** 变更说明（如「新增字典项: order_paid」） */
  private String changeLog;

  /** 变更前全量字典项 JSON 快照（用于回滚） */
  private String snapshotJson;

  /** 生效时间（版本被创建时间） */
  private LocalDateTime effectiveDate;
}
