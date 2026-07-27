package com.njydsz.agent.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.agent.domain.dto.post.AgentDefinitionDOPostDTO;
import com.njydsz.agent.domain.dto.put.AgentDefinitionDOPutDTO;

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


    // ===== AgentDefinitionDO PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentDefinitionDO postDtoToEntity(AgentDefinitionDOPostDTO dto);

    // ===== AgentDefinitionDO PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentDefinitionDO putDtoToEntity(AgentDefinitionDOPutDTO dto);

}