package com.njydsz.message.infra.repository;

import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.dto.MsgCanaryDTO;
import com.njydsz.message.domain.repository.MsgCanaryRepository;
import com.njydsz.message.domain.vo.MsgCanaryVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgCanary;
import com.njydsz.message.infra.mapper.MsgCanaryMapper;

/**
 * 灰度实验仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgCanaryRepository} 接口，封装 MsgCanaryMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link MessageConverter} 实现 DO ↔ VO ↔ DTO 的双向转换
 *   <li>CUD 入参使用领域 DTO，返回值使用领域 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgCanaryRepositoryImpl implements MsgCanaryRepository {

  private final MsgCanaryMapper msgCanaryMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgCanaryDTO dto) {
    MsgCanary entity = converter.dtoToDO(dto);
    return msgCanaryMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgCanaryDTO dto) {
    MsgCanary entity = converter.dtoToDO(dto);
    return msgCanaryMapper.updateById(entity) > 0;
  }

  @Override
  public Optional<MsgCanaryVO> findById(String id) {
    return Optional.ofNullable(msgCanaryMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public Optional<MsgCanaryVO> findByCanaryKey(String canaryKey) {
    QueryWrapper<MsgCanary> wrapper = new QueryWrapper<>();
    wrapper.eq("canary_key", canaryKey);
    return Optional.ofNullable(msgCanaryMapper.selectOne(wrapper)).map(converter::doToVO);
  }
}
