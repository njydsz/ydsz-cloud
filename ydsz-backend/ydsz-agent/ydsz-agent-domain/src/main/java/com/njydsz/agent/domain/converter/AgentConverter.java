package com.njydsz.agent.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

/**
 * agent 模块统一 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface AgentConverter {

    AgentConverter INSTANT = Mappers.getMapper(AgentConverter.class);

    // ===== AgentDefinitionDO =====
    AgentDefinitionVO entityToVO(AgentDefinitionDO entity);
    List<AgentDefinitionVO> agentDefinitionDOListToVO(List<AgentDefinitionDO> entities);

}