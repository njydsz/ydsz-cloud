package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 异步任务提交请求 DTO
 *
 * <p>封装提交异步任务时客户端需要提供的参数：任务类型、输入参数 JSON 以及可选自定义超时秒数。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "异步任务提交请求")
public class AsyncTaskSubmitDTO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 任务类型编码（REPORT_GENERATE / DOC_INGEST / BATCH_CHAT / CODE_EXECUTION） */
  @NotBlank(message = "任务类型不能为空")
  @Schema(description = "任务类型编码", example = "REPORT_GENERATE")
  private String taskType;

  /** 任务输入参数 JSON（可选，null 表示无输入） */
  @Schema(description = "任务输入参数（JSON 字符串）")
  private String inputPayload;

  /** 自定义超时秒数（可选，null 时使用任务类型默认值） */
  @Schema(description = "自定义超时秒数（可选）", example = "300")
  private Long timeoutSeconds;
}
