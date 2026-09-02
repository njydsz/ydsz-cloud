package com.njydsz.system.server.service.rollback;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;




/**
 * 字典项回滚策略 — 从快照 JSON 反序列化并重建字典项资源。
 *
 * <p>负责：
 *
 * <ol>
 *   <li>物理删除当前字典项
 *   <li>反序列化快照 JSON 并重建字典项
 *   <li>精准失效相关缓存
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictItemRollbackStrategy implements RollbackStrategy {

  private final DictRepository dictRepository;
  private final CacheManager cacheManager;
  private final CacheKeyBuilder cacheKeyBuilder;

  @Override
  public void rebuild(String snapshotJson) {
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return;
    }

    String typeCode;
    try {
      List<DictItemVO> snapshotItems =
          YdszJson.fromJson(snapshotJson, List.class, DictItemVO.class);
      if (snapshotItems == null || snapshotItems.isEmpty()) {
        return;
      }

      // 从快照中获取 typeCode（所有项属于同一类型）
      typeCode = snapshotItems.get(0).getTypeCode();

      // 1. 物理删除当前字典项
      dictRepository.physicalDeleteByTypeCode(typeCode);

      // 2. 重建字典项
      for (DictItemVO vo : snapshotItems) {
        DictItemDTO dto = toDto(vo);
        dto.setId(vo.getId());
        dictRepository.insertItem(dto);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
          .data("reason", e.getMessage());
    }

    // 3. 精准失效缓存
    evictDictList(typeCode);
  }

  /**
   * 精准失效字典列表缓存。
   *
   * @param typeCode 字典类型编码
   */
  private void evictDictList(String typeCode) {
    Cache cache = cacheManager.getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE);
    if (cache != null) {
      cache.evict(cacheKeyBuilder.dictList(typeCode));
    }
  }

  /**
   * DictItemVO → DictItemDTO 转换（私有）。
   *
   * @param vo 字典项 VO
   * @return 字典项 DTO
   */
  private DictItemDTO toDto(DictItemVO vo) {
    DictItemDTO dto = new DictItemDTO();
    dto.setId(vo.getId());
    dto.setTypeCode(vo.getTypeCode());
    dto.setItemCode(vo.getItemCode());
    dto.setItemValue(vo.getItemValue());
    dto.setSortOrder(vo.getSortOrder());
    dto.setStatus(vo.getStatus());
    dto.setParentId(vo.getParentId());
    dto.setDescription(vo.getDescription());
    return dto;
  }
}
