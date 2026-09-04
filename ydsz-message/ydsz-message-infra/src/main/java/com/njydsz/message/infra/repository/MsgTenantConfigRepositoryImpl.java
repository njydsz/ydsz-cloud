package com.njydsz.message.infra.repository;

import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.MsgTenantConfigDTO;
import com.njydsz.message.domain.entity.MsgTenantConfig;
import com.njydsz.message.domain.repository.MsgTenantConfigRepository;
import com.njydsz.message.domain.vo.MsgTenantConfigVO;
import com.njydsz.message.infra.mapper.MsgTenantConfigMapper;

/**
 * 多租户消息配置仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgTenantConfigRepository} 接口，封装 MsgTenantConfigMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link MessageConverter} 实现 VO ↔ Entity ↔ DTO 的双向转换
 *   <li>CUD 入参使用领域 DTO，返回值使用领域 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgTenantConfigRepositoryImpl implements MsgTenantConfigRepository {

  private final MsgTenantConfigMapper msgTenantConfigMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgTenantConfigDTO dto) {
    MsgTenantConfig entity = converter.dtoToEntity(dto);
    return msgTenantConfigMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgTenantConfigDTO dto) {
    MsgTenantConfig entity = converter.dtoToEntity(dto);
    return msgTenantConfigMapper.updateById(entity) > 0;
  }

  @Override
  public Optional<MsgTenantConfigVO> findById(String id) {
    return Optional.ofNullable(msgTenantConfigMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<MsgTenantConfigVO> findByTenantId(String tenantId) {
    QueryWrapper<MsgTenantConfig> wrapper = new QueryWrapper<>();
    wrapper.eq("tenant_id", tenantId);
    return Optional.ofNullable(msgTenantConfigMapper.selectOne(wrapper)).map(converter::entityToVO);
  }
}
