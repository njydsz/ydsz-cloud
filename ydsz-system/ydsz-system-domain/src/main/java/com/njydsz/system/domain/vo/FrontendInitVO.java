package com.njydsz.system.domain.vo;

import java.util.List;
import java.util.Map;

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
 * @since 26.09.01
 */
@Data
@Builder
public class FrontendInitVO {

  /** 公开配置 Map（key-value 形式，前端直接使用） */
  private Map<String, String> publicConfigs;

  /** 字典类型下拉数据（typeCode -> 字典项列表） */
  private Map<String, List<DictItemVO>> dictMap;

  /** 系统版本号 */
  private String systemVersion;

  /** 当前租户 ID */
  private String tenantId;

  /** 当前用户 ID */
  private String userId;
}
