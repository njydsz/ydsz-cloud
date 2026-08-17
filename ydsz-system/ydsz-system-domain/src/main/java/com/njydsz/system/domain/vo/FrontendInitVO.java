package com.njydsz.system.domain.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 前端初始化聚合响应 VO
 *
 * <p>聚合前端初始化所需的数据，减少前端启动时的请求次数。
 *
 * <p>包含：
 *
 * <ul>
 *   <li>公开配置（前端可读的系统配置）
 *   <li>字典类型下拉数据（常用字典）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@Builder
@Schema(description = "前端初始化聚合响应")
public class FrontendInitVO {

  /** 公开配置 Map（key-value 形式，前端直接使用） */
  @Schema(description = "公开配置 Map")
  private Map<String, String> publicConfigs;

  /** 字典类型下拉数据（typeCode -> 字典项列表） */
  @Schema(description = "常用字典数据")
  private Map<String, List<DictItemVO>> dictMap;

  /** 系统版本号 */
  @Schema(description = "系统版本号")
  private String systemVersion;

  /** 当前租户 ID */
  @Schema(description = "当前租户 ID")
  private String tenantId;

  /** 当前用户 ID */
  @Schema(description = "当前用户 ID")
  private String userId;
}
