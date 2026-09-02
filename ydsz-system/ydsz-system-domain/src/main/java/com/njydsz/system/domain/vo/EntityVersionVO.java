package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 统一实体版本 VO
 *
 * <p>对应 {@code ydsz_sys_entity_version} 表的展示视图，是「版本管理」列表 / 详情接口的返回值类型。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.system.domain.entity.EntityVersion 实体版本实体
 */
@Data
public class EntityVersionVO {

  private String id;

  private String resourceType;

  private String resourceKey;

  private String resourceGroup;

  private String version;

  private String changeLog;

  private LocalDateTime effectiveDate;

  private String snapshotJson;
}
