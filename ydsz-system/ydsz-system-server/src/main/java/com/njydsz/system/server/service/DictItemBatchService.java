package com.njydsz.system.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.system.domain.dto.DictItemDTO;

/**
 * 字典项批量操作 Service 接口
 *
 * <p>提供批量新增、批量导入（Excel）、批量导出等能力。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public interface DictItemBatchService {

  /**
   * 批量新增字典项
   *
   * <p>同一事务内执行，任意一条失败则全部回滚。
   *
   * <p>批量内重复校验：先校验批量内无重复 (typeCode, itemCode)， 再逐条与 DB 比对唯一性。
   *
   * @param items 字典项列表
   * @return 操作结果 {successCount, failCount, versionId}
   */
  Map<String, Object> batchSave(List<DictItemDTO> items);
}
