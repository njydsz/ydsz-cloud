package com.njydsz.system.domain.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 前端错误批量上报 DTO
 *
 * <p>对应 ydsz-micro 中 {@code @ydsz/monitor} 上报体 {@code { "errors": [...] }}，
 * 上报端点为 {@code POST /monitor/error}。前端批量通道上限为单批 20 条，
 * 服务端放宽至 50 条以兼容自定义上报端点的场景，同时限制单请求体规模。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Data
public class MonitorErrorBatchDTO {

  /** 错误条目列表（单批上限 50 条，超出返回参数校验失败） */
  @NotEmpty(message = "错误列表不能为空")
  @Size(max = 50, message = "单批错误最多 50 条")
  @Valid
  private List<MonitorErrorDTO> errors;
}
