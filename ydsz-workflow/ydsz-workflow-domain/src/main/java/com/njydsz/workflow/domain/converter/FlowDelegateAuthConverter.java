package com.njydsz.workflow.domain.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.workflow.domain.dto.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;

/**
 * 流程委派授权 MapStruct 转换器（Domain 层）。
 *
 * <p>承担 FlowDelegateAuthPostDTO → FlowDelegateAuthVO 的直接转换，
 * 避免 server 层依赖 infra 层的 WorkflowConverter。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowDelegateAuthConverter {

  /** MapStruct 单例实例 */
  FlowDelegateAuthConverter INSTANT = Mappers.getMapper(FlowDelegateAuthConverter.class);

  /**
   * DTO → VO 转换。
   *
   * @param dto 委派授权新增请求 DTO
   * @return 委派授权 VO
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "authStatus", ignore = true)
  @Mapping(target = "providerTraceId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FlowDelegateAuthVO postDtoToVO(FlowDelegateAuthPostDTO dto);
}
