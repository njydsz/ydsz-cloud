package com.njydsz.userinfo.domain.query;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.common.core.request.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志分页查询条件。
 *
 * <p>支持按用户名、IP、登录结果、时间范围筛选。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginLogPageQuery extends PageQuery implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名（模糊匹配） */
  private String username;

  /** 登录 IP（精确或模糊匹配） */
  private String loginIp;

  /** 登录结果（SUCCESS/FAILED/ALL） */
  private String status;

  /** 起始时间（含） */
  private LocalDateTime startTime;

  /** 结束时间（含） */
  private LocalDateTime endTime;
}
