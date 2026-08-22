package com.njydsz.system.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.system.domain.vo.DictItemVO;

/**
 * 字典项批量操作 DTO
 *
 * <p>用于批量新增字典项（运营初始化场景），单次最多 500 条。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DictItemBatchDTO {

  @NotEmpty(message = "字典项列表不能为空")
  @Size(max = 500, message = "单次批量新增最多 500 条")
  private List<DictItemVO> items;
}
