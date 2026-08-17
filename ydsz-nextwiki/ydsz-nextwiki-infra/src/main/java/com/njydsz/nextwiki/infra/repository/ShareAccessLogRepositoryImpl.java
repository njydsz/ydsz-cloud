package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.infra.entity.ShareAccessLogDO;
import com.njydsz.nextwiki.domain.repository.ShareAccessLogRepository;
import com.njydsz.nextwiki.infra.mapper.ShareAccessLogMapper;

/**
 * 分享访问日志仓储实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ShareAccessLogRepositoryImpl implements ShareAccessLogRepository {

  private final ShareAccessLogMapper shareAccessLogMapper;

  @Override
  public ShareAccessLogDO save(ShareAccessLogDO log) {
    shareAccessLogMapper.insert(log);
    return log;
  }

  @Override
  public List<ShareAccessLogDO> findByShareId(String shareId, int limit) {
    return shareAccessLogMapper.selectByShareId(shareId, limit);
  }

  @Override
  public List<Map<String, Object>> countDailyAccess(String shareId, int days) {
    return shareAccessLogMapper.countDailyAccess(shareId, days);
  }
}
