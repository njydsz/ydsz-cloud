package com.njydsz.system.server.service.rollback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.VariableRepository;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;



/**
 * 系统变量回滚策略 — 从快照 JSON 反序列化并重建变量资源。
 *
 * <p>负责：
 *
 * <ol>
 *   <li>反序列化快照 JSON 为 VariableVO
 *   <li>更新现有变量或重新创建（原变量已被删除时）
 *   <li>精准失效相关缓存
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VariableRollbackStrategy implements RollbackStrategy {

  private final VariableRepository variableRepository;
  private final CacheManager cacheManager;
  private final CacheKeyBuilder cacheKeyBuilder;

  @Override
  public void rebuild(String snapshotJson) {
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return;
    }

    try {
      VariableVO snapshotVO = YdszJson.fromJson(snapshotJson, VariableVO.class);

      VariableVO currentVariable = variableRepository.findByKeyIgnoreStatus(snapshotVO.getVariableKey()).orElse(null);
      if (currentVariable != null) {
        // 更新现有变量
        VariableDTO updateDto = toDto(snapshotVO);
        updateDto.setId(currentVariable.getId());
        variableRepository.updateById(updateDto);
      } else {
        // 原变量已被删除，重新创建
        variableRepository.insert(toDto(snapshotVO));
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
          .data("reason", e.getMessage());
    }

    // 精准失效缓存
    evictVariable(snapshotJson);
  }

  /**
   * 精准失效变量缓存。
   *
   * @param snapshotJson 快照 JSON（含 variableKey 用于定位缓存键）
   */
  private void evictVariable(String snapshotJson) {
    try {
      VariableVO snapshotVO = YdszJson.fromJson(snapshotJson, VariableVO.class);
      Cache cache = cacheManager.getCache(CacheConstants.SYSTEM_VARIABLE_CACHE);
      if (cache != null) {
        cache.evict(cacheKeyBuilder.variable(snapshotVO.getVariableKey()));
      }
    } catch (Exception e) {
      // 缓存失效失败不影响主流程
      log.warn("[VariableRollbackStrategy] 解析快照失效缓存失败: {}", e.getMessage());
    }
  }

  /**
   * VariableVO → VariableDTO 转换（私有）。
   *
   * @param vo 变量 VO
   * @return 变量 DTO
   */
  private VariableDTO toDto(VariableVO vo) {
    VariableDTO dto = new VariableDTO();
    dto.setId(vo.getId());
    dto.setVariableKey(vo.getVariableKey());
    dto.setVariableValue(vo.getVariableValue());
    dto.setValueType(vo.getValueType());
    dto.setDescription(vo.getDescription());
    dto.setStatus(vo.getStatus());
    return dto;
  }
}
