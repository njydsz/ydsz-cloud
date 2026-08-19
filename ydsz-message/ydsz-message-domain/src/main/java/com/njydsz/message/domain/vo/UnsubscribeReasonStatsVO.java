package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退订原因统计视图对象（VO）。
 *
 * <p>供管理后台展示退订原因分布统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "退订原因统计")
public class UnsubscribeReasonStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 退订原因 */
  private String reason;

  /** 退订次数 */
  private Long count;
}
