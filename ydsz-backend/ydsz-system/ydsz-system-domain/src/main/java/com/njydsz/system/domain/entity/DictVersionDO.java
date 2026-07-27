package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 字典版本 DO。
 *
 * <p>对应数据库表 {@code ydsz_dict_version}，记录字典变更历史快照，支持回滚与变更审计。
 * 字段与 DDL 完全对齐（含 tenant_id/updated_by/updated_at）。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_version")
public class DictVersionDO extends MpBaseEntity<String> {

    private String tenantId;

    private String typeCode;
    private String version;
    private String changeLog;
    private String snapshotJson;
    private LocalDateTime effectiveDate;

}
