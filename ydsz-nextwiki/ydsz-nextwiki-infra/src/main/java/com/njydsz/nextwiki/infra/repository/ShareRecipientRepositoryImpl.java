package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.infra.mapper.ShareRecipientMapper;

/**
 * 分享目标用户仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ShareRecipientRepositoryImpl implements ShareRecipientRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final ShareRecipientMapper shareRecipientMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 批量新增分享目标用户（定向分享）。
   *
   * @param dtos 分享目标用户数据传输对象列表
   */
  @Override
  public void saveBatch(List<ShareRecipientDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    List<ShareRecipient> entities = mapper.shareRecipientListToEntity(dtos);
    for (ShareRecipient entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      shareRecipientMapper.insert(entity);
    }
  }

  /**
   * 按分享链接 ID 查询目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户视图对象列表
   */
  @Override
  public List<ShareRecipientVO> findByShareId(String shareId) {
    return mapper.shareRecipientListToVO(shareRecipientMapper.selectByShareId(shareId));
  }

  /**
   * 按用户 ID 查询该用户作为目标用户的分享记录（"我收到的分享"）。
   *
   * @param recipientId 目标用户 ID
   * @return 目标用户视图对象列表
   */
  @Override
  public List<ShareRecipientVO> findByRecipientId(String recipientId) {
    return mapper.shareRecipientListToVO(
        shareRecipientMapper.selectByRecipientId(recipientId));
  }

  /**
   * 标记目标用户已查看分享。
   *
   * @param shareId 分享链接 ID
   * @param recipientId 目标用户 ID
   * @return 更新记录数
   */
  @Override
  public int markAsViewed(String shareId, String recipientId) {
    return shareRecipientMapper.markAsViewed(shareId, recipientId);
  }
}
