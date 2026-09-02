package com.njydsz.cronjob.domain.dto.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 任务批量操作 DTO
 *
 * <p>用于批量暂停/恢复/触发/删除任务的请求参数。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "任务批量操作 DTO")
public class JobBatchDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotEmpty(message = "任务 ID 列表不能为空")
  @Schema(description = "任务 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
  private List<String> jobIds;
}
