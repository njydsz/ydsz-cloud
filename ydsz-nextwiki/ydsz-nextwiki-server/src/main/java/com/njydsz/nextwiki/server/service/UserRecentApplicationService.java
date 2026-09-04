package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.nextwiki.domain.dto.UserRecentDTO;
import com.njydsz.nextwiki.domain.repository.UserRecentRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.UserRecentVO;
import com.njydsz.nextwiki.domain.converter.NextwikiConverter;

/**
 * 用户最近访问应用服务
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>提供用户最近访问记录的管理功能：记录访问、查看最近访问列表、清理记录。 每用户最多保留 100 条最近访问记录，超出自动清理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRecentApplicationService {

  private final UserRecentRepository userRecentRepository;
  private final FileApplicationService fileApplicationService;
  private final NextwikiConverter nextwikiConverter;

  /** 默认查询数量限制 */
  private static final int DEFAULT_LIMIT = 20;

  /**
   * 记录一次文件访问。
   *
   * @param nodeId 节点ID
   * @param userId 用户ID
   * @param accessType 访问类型（view / edit / download）
   */
  @Transactional(rollbackFor = Exception.class)
  public void recordAccess(String nodeId, String userId, String accessType) {
    String tenantId = TenantContextHolder.getTenantId();
    LocalDateTime now = LocalDateTime.now();

    UserRecentDTO dto = UserRecentDTO.builder()
        .userId(userId)
        .nodeId(nodeId)
        .tenantId(tenantId)
        .accessType(accessType != null ? accessType : "view")
        .accessedAt(now)
        .build();

    userRecentRepository.saveOrUpdate(dto);

    log.debug(
        "[UserRecentApplicationService] 记录访问: nodeId={}, userId={}, type={}",
        nodeId,
        userId,
        accessType);
  }

  /**
   * 查询用户最近访问列表。
   *
   * @param userId 用户ID
   * @return 最近访问视图列表
   */
  public List<UserRecentVO> listRecent(String userId) {
    return listRecent(userId, DEFAULT_LIMIT);
  }

  /**
   * 查询用户最近访问列表（带数量限制）。
   *
   * @param userId 用户ID
   * @param limit 返回数量限制
   * @return 最近访问视图列表
   */
  public List<UserRecentVO> listRecent(String userId, int limit) {
    String tenantId = TenantContextHolder.getTenantId();
    List<UserRecentDTO> recents =
        userRecentRepository.findByUserIdOrderByAccessedAt(userId, tenantId, limit);

    // 转换为 VO（含节点元数据）
    List<UserRecentVO> result = new ArrayList<>(recents.size());
    for (UserRecentDTO recent : recents) {
      FileNodeVO node = null;
      try {
        node = fileApplicationService.getFileInfo(recent.getNodeId());
      } catch (Exception e) {
        log.warn("[UserRecentApplicationService] 最近访问节点已删除: nodeId={}", recent.getNodeId());
        continue; // 跳过已删除的节点
      }

      if (node != null) {
        result.add(UserRecentVO.builder()
            .nodeId(recent.getNodeId())
            .name(node.getName())
            .nodeType(node.getNodeType())
            .suffix(node.getSuffix())
            .size(node.getSize())
            .path(node.getPath())
            .thumbnailKey(node.getThumbnailKey())
            .accessType(recent.getAccessType())
            .accessedAt(recent.getAccessedAt())
            .updatedBy(node.getUpdatedBy())
            .updatedAt(node.getUpdatedAt())
            .build());
      }
    }

    return result;
  }

  /**
   * 清除用户所有最近访问记录。
   *
   * @param userId 用户ID
   * @return 是否成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean clearAll(String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    int deleted = userRecentRepository.deleteEarliestRecords(userId, tenantId, 0);
    log.info("[UserRecentApplicationService] 清除最近访问记录: userId={}, deleted={}", userId, deleted);
    return true;
  }

  /**
   * 获取用户最近访问记录数量。
   *
   * @param userId 用户ID
   * @return 记录数量
   */
  public int getRecentCount(String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    return userRecentRepository.countByUserId(userId, tenantId);
  }
}
