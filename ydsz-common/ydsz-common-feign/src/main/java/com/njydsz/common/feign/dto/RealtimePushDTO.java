package com.njydsz.common.feign.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实时推送数据传输对象。
 *
 * <p>用于 WebSocket/SSE 广播场景，承载推送到前端的数据载荷。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimePushDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 推送类型标识 */
  private String type;

  /** 推送数据载荷 */
  private Map<String, Object> data;
}
