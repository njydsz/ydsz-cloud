package com.njydsz.userinfo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;


/**
 * 社交平台客户端配置分页查询参数（P1-1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SocialClientPageQuery extends PageQuery {

  /** 平台标识（精确匹配） */
  private String platform;

  /** 平台显示名称（模糊查询） */
  private String platformName;

  /** 状态过滤：ENABLED / DISABLED */
  private String status;
}
