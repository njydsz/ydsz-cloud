package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.infra.mapper.ShareLinkMapper;

/**
 * 分享链接仓储实现
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
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ShareLinkRepositoryImpl implements ShareLinkRepository {

  /** 分布式 ID 生成器（Snowflake 算法，生成分享链接唯一 ID） */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 分享链接 MyBatis Mapper（数据库 CRUD 原始操作） */
  private final ShareLinkMapper shareLinkMapper;

  /** DTO/VO/DO 转换器（实体与视图对象之间的映射） */
  private final NextwikiConverter converter;

  @Override
  public ShareLinkVO save(ShareLinkDTO dto) {
    ShareLink entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    shareLinkMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public Optional<ShareLinkVO> findById(String id) {
    return Optional.ofNullable(shareLinkMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<ShareLinkVO> findByShareCode(String shareCode) {
    return Optional.ofNullable(shareLinkMapper.selectByShareCode(shareCode))
        .map(converter::entityToVO);
  }

  @Override
  public List<ShareLinkVO> findByFileNodeId(String fileNodeId) {
    return converter.shareLinkListToVO(shareLinkMapper.selectByFileNodeId(fileNodeId));
  }

  @Override
  public List<ShareLinkVO> findActiveSharesByUserId(String userId) {
    return converter.shareLinkListToVO(shareLinkMapper.selectActiveSharesByUserId(userId));
  }

  @Override
  public void update(ShareLinkDTO dto) {
    ShareLink entity = converter.dtoToEntityWithId(dto);
    shareLinkMapper.updateById(entity);
  }

  @Override
  public void revoke(String id) {
    shareLinkMapper.revoke(id);
  }

  @Override
  public void incrementAccessCount(String id) {
    shareLinkMapper.incrementAccessCount(id);
  }

  @Override
  public List<ShareLinkVO> findExpiringShares(int withinHours) {
    return converter.shareLinkListToVO(shareLinkMapper.selectExpiringShares(withinHours));
  }
}
