package com.njydsz.system.domain.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Web Vitals 批量上报 DTO
 *
 * <p>对应 ydsz-micro 中 {@code @ydsz/monitor} 上报体 {@code { "vitals": [...] }}，
 * 上报端点为 {@code POST /monitor/web-vitals}（超出性能阈值时附加 {@code ?alert=true}）。
 * 前端批量缓冲上限为 6 条，服务端放宽至 20 条以兼容自定义上报端点场景。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Data
public class MonitorWebVitalBatchDTO {

  /** 指标条目列表（单批上限 20 条，超出返回参数校验失败） */
  @NotEmpty(message = "指标列表不能为空")
  @Size(max = 20, message = "单批指标最多 20 条")
  @Valid
  private List<MonitorWebVitalDTO> vitals;
}
