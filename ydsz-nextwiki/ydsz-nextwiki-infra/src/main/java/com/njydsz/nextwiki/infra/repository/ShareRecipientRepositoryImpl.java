package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.infra.entity.ShareRecipientDO;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.infra.mapper.ShareRecipientMapper;

/**
 * 分享目标用户仓储实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ShareRecipientRepositoryImpl implements ShareRecipientRepository {

  private final ShareRecipientMapper shareRecipientMapper;

  @Override
  public void saveBatch(List<ShareRecipientDO> recipients) {
    for (ShareRecipientDO recipient : recipients) {
      shareRecipientMapper.insert(recipient);
    }
  }

  @Override
  public List<ShareRecipientDO> findByShareId(String shareId) {
    return shareRecipientMapper.selectByShareId(shareId);
  }

  @Override
  public List<ShareRecipientDO> findByRecipientId(String recipientId) {
    return shareRecipientMapper.selectByRecipientId(recipientId);
  }

  @Override
  public int markAsViewed(String shareId, String recipientId) {
    return shareRecipientMapper.markAsViewed(shareId, recipientId);
  }
}
