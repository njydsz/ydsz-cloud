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
 * @since 26.10.01
 */
@Data
public class DictItemBatchDTO {

  @NotEmpty(message = "{system.dto.dictItemBatch.items.required}")
  @Size(max = 500, message = "{system.dto.dictItemBatch.items.max}")
  private List<DictItemVO> items;
}
