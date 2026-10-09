package com.njydsz.system.domain.query;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分布式锁分页查询参数（ydsz-system-domain）。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class LockPageQuery implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 页码（从 1 开始） */
  @Min(value = 1, message = "页码最小为 1")
  private Integer pageNum = 1;

  /** 每页条数（1-100） */
  @Min(value = 1, message = "每页条数最小为 1")
  @Max(value = 100, message = "每页条数最大为 100")
  private Integer pageSize = 20;

  /** lockKey 模糊搜索（可选） */
  private String lockKey;

  /** 持有者筛选（可选） */
  private String owner;
}
