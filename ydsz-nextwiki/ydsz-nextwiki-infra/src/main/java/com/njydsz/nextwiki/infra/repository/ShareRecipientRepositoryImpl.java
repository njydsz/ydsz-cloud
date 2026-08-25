package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.ShareRecipient;
import com.njydsz.nextwiki.infra.mapper.ShareRecipientMapper;

/**
 * 分享目标用户仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ShareRecipientRepositoryImpl implements ShareRecipientRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final ShareRecipientMapper shareRecipientMapper;
  private final NextwikiConverter converter;

  @Override
  public void saveBatch(List<ShareRecipientDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    List<ShareRecipient> entities = converter.shareRecipientDtosToEntities(dtos);
    for (ShareRecipient entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      shareRecipientMapper.insert(entity);
    }
  }

  @Override
  public List<ShareRecipientVO> findByShareId(String shareId) {
    return converter.shareRecipientListToVO(shareRecipientMapper.selectByShareId(shareId));
  }

  @Override
  public List<ShareRecipientVO> findByRecipientId(String recipientId) {
    return converter.shareRecipientListToVO(
        shareRecipientMapper.selectByRecipientId(recipientId));
  }

  @Override
  public int markAsViewed(String shareId, String recipientId) {
    return shareRecipientMapper.markAsViewed(shareId, recipientId);
  }
}
