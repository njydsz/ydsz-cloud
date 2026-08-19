package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.query.MsgVariableSourceQuery;
import com.njydsz.message.domain.repository.MsgVariableSourceRepository;
import com.njydsz.message.domain.vo.MsgVariableSourceVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgVariableSourceDO;
import com.njydsz.message.infra.mapper.config.MsgVariableSourceMapper;

/**
 * 消息变量数据源仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgVariableSourceRepository} 接口，封装 MsgVariableSourceMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgVariableSourceRepositoryImpl implements MsgVariableSourceRepository {

  private final MsgVariableSourceMapper msgVariableSourceMapper;

  private final MessageConverter converter;

  @Override
  public List<MsgVariableSourceVO> findList(MsgVariableSourceQuery query) {
    QueryWrapper<MsgVariableSourceDO> wrapper = new QueryWrapper<>();
    if (query.getTemplateCode() != null && !query.getTemplateCode().isBlank()) {
      wrapper.eq("template_code", query.getTemplateCode());
    }
    if (query.getVariableName() != null && !query.getVariableName().isBlank()) {
      wrapper.eq("variable_name", query.getVariableName());
    }
    if (query.getSourceType() != null && !query.getSourceType().isBlank()) {
      wrapper.eq("source_type", query.getSourceType());
    }
    wrapper.eq("deleted", 0);
    return converter.variableSourceDoListToVO(msgVariableSourceMapper.selectList(wrapper));
  }
}
