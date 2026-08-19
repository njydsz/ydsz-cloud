package com.njydsz.nextwiki.server.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.service.TrashDomainService;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;
import com.njydsz.nextwiki.server.service.SearchApplicationService;

/**
 * NextWiki 定时任务
 *
 * <p>自动清理回收站过期条目、搜索索引重建。 使用分布式锁确保多实例部署时同一任务不会被并发执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextwikiScheduledJobs {

  private final TrashDomainService trashDomainService;
  private final TrashItemRepository trashItemRepository;
  private final SearchApplicationService searchApplicationService;

  /** 每天凌晨 2 点清理过期回收站条目 */
  @Scheduled(cron = "0 0 2 * * ?")
  @DistributedScheduled(lockKey = "nextwiki:cleanup-trash")
  public void cleanupExpiredTrash() {
    log.info("[NextwikiScheduledJobs] 开始清理过期回收站条目");
    List<TrashItemVO> expiredVOs = trashItemRepository.findExpiredItems(100);
    List<TrashItemDTO> expiredDTOs = expiredVOs.stream().map(vo -> {
      TrashItemDTO dto = new TrashItemDTO();
      dto.setId(vo.getId());
      dto.setFileNodeId(vo.getFileNodeId());
      dto.setOriginalName(vo.getOriginalName());
      dto.setOriginalPath(vo.getOriginalPath());
      dto.setOriginalParentId(vo.getOriginalParentId());
      dto.setNodeType(vo.getNodeType());
      dto.setSize(vo.getSize());
      dto.setDeletedTime(vo.getDeletedTime());
      dto.setPurgeTime(vo.getPurgeTime());
      dto.setStatus(vo.getStatus());
      return dto;
    }).collect(Collectors.toList());
    int cleaned = trashDomainService.cleanupExpiredItems(expiredDTOs, SystemConstants.SYSTEM_USER_ID);
    log.info("[NextwikiScheduledJobs] 清理完成: count={}", cleaned);
  }

  /** 每周日凌晨 3 点重建搜索索引 */
  @Scheduled(cron = "0 0 3 * * SUN")
  @DistributedScheduled(lockKey = "nextwiki:rebuild-index")
  public void rebuildSearchIndex() {
    log.info("[NextwikiScheduledJobs] 开始重建搜索索引");
    searchApplicationService.rebuildAllIndices();
    log.info("[NextwikiScheduledJobs] 搜索索引重建完成");
  }
}
