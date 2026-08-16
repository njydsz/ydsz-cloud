package com.njydsz.agent.domain.converter;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import com.njydsz.agent.domain.dto.post.AgentDefinitionPostDTO;
import com.njydsz.agent.domain.dto.put.AgentDefinitionPutDTO;
import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

/**
 * agent 模块统一 MapStruct 转换器。
 *
 * <p>负责 Entity ↔ VO、PostDTO → Entity、PutDTO → Entity 之间的类型转换。
 * 系统字段（id/deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt）
 * 由框架自动填充，转换时忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface AgentConverter {

    /** MapStruct 实例 */
    AgentConverter INSTANT = Mappers.getMapper(AgentConverter.class);

    // ===== AgentDefinitionDO =====

    /**
     * Entity → VO 转换
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    AgentDefinitionVO entityToVO(AgentDefinitionDO entity);

    /**
     * Entity 列表 → VO 列表转换
     *
     * @param entities 实体列表
     * @return VO 列表
     */
    List<AgentDefinitionVO> agentDefinitionListToVO(List<AgentDefinitionDO> entities);


    // ===== AgentDefinition PostDTO → Entity =====

    /**
     * PostDTO → Entity 转换（创建场景，系统字段自动忽略）
     *
     * @param dto 创建请求 DTO
     * @return 数据库实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentDefinitionDO postDtoToEntity(AgentDefinitionPostDTO dto);

    // ===== AgentDefinition PutDTO → Entity =====

    /**
     * PutDTO → Entity 转换（更新场景，系统字段自动忽略）
     *
     * @param dto 更新请求 DTO
     * @return 数据库实体
     */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentDefinitionDO putDtoToEntity(AgentDefinitionPutDTO dto);

}
