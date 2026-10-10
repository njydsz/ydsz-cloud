package com.njydsz.system.domain.dto;

import java.util.List;

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
 * @since 26.10.01
 */
@Data
public class ConfigBatchDTO {

  @NotEmpty(message = "{system.dto.configBatch.items.required}")
  @Size(max = 500, message = "{system.dto.configBatch.items.max}")
  private List<ConfigVO> items;
}
