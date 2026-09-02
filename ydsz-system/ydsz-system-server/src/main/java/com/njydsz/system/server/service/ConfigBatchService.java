package com.njydsz.system.server.service;
import java.util.List;
import java.util.Map;

import com.njydsz.system.domain.vo.ConfigVO;



/**
 * 系统配置批量操作 Service 接口
 *
 * <p>提供批量创建、批量更新等能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ConfigBatchService {

  /**
   * 批量创建配置项
   *
   * <p>同一事务内执行，任意一条失败则全部回滚。
   *
   * <p>批量内重复校验：先校验批量内无重复 (configGroup, configKey)， 再逐条与 DB 比对唯一性。
   *
   * @param items 配置列表
   * @return 操作结果 {successCount, totalCount, message}
   */
  Map<String, Object> batchSave(List<ConfigVO> items);
}
