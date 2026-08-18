package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;
import com.njydsz.nextwiki.domain.repository.ShareAccessLogRepository;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.ShareAccessLogDO;
import com.njydsz.nextwiki.infra.mapper.ShareAccessLogMapper;

/**
 * 分享访问日志仓储实现
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
public class ShareAccessLogRepositoryImpl implements ShareAccessLogRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final ShareAccessLogMapper shareAccessLogMapper;
  private final NextwikiConverter converter;

  @Override
  public ShareAccessLogVO save(ShareAccessLogDTO dto) {
    ShareAccessLogDO entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    shareAccessLogMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public List<ShareAccessLogVO> findByShareId(String shareId, int limit) {
    return converter.shareAccessLogListToVO(shareAccessLogMapper.selectByShareId(shareId, limit));
  }

  @Override
  public List<Map<String, Object>> countDailyAccess(String shareId, int days) {
    return shareAccessLogMapper.countDailyAccess(shareId, days);
  }
}
