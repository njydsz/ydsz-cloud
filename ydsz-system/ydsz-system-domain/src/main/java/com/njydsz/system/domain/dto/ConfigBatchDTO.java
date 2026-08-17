package com.njydsz.system.domain.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置批量操作 DTO
 *
 * <p>用于批量创建配置项（运营初始化场景），单次最多 500 条。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@Schema(description = "系统配置批量操作请求")
public class ConfigBatchDTO {

  @NotEmpty(message = "配置列表不能为空")
  @Size(max = 500, message = "单次批量创建最多 500 条")
  @Schema(description = "待创建的配置列表")
  private List<ConfigVO> items;
}
