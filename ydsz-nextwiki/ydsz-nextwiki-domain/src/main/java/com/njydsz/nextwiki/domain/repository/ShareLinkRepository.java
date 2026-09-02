package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;

/**
 * 分享链接仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link ShareLinkVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link ShareLinkDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ShareLinkRepository {

  /**
   * 保存分享链接（新增或更新）
   *
   * @param dto 分享链接 DTO
   * @return 持久化后的分享链接 VO
   */
  ShareLinkVO save(ShareLinkDTO dto);

  /**
   * 按 ID 查询分享链接
   *
   * @param id 分享链接ID
   * @return 分享链接 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<ShareLinkVO> findById(String id);

  /**
   * 按分享码查询分享链接
   *
   * @param shareCode 分享码
   * @return 分享链接 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<ShareLinkVO> findByShareCode(String shareCode);

  /**
   * 查询某文件节点关联的全部分享链接
   *
   * @param fileNodeId 文件节点ID
   * @return 分享链接 VO 列表
   */
  List<ShareLinkVO> findByFileNodeId(String fileNodeId);

  /**
   * 查询某用户的全部有效分享链接
   *
   * @param userId 用户ID
   * @return 有效分享链接 VO 列表
   */
  List<ShareLinkVO> findActiveSharesByUserId(String userId);

  /**
   * 更新分享链接
   *
   * @param dto 分享链接 DTO
   */
  void update(ShareLinkDTO dto);

  /**
   * 撤销分享
   *
   * @param id 分享链接ID
   */
  void revoke(String id);

  /**
   * 原子递增分享链接的已访问次数
   *
   * @param id 分享链接ID
   */
  void incrementAccessCount(String id);

  /**
   * 查询即将到期的活跃分享链接
   *
   * @param withinHours 多少小时内即将到期
   * @return 即将到期的分享链接 VO 列表
   */
  List<ShareLinkVO> findExpiringShares(int withinHours);
}
