package com.njydsz.generator.api.assembler;

import com.njydsz.generator.api.dto.CodeGenResultDTO;
import com.njydsz.generator.api.dto.TableMetaDTO;
import com.njydsz.generator.vo.GenResultVO;
import com.njydsz.generator.vo.CodePreviewVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 代码生成器 Assembler（MapStruct）。
 *
 * <p>负责 domain VO → Feign DTO 的转换。
 *
 * <p><b>DDD 分层位置：</b>api 模块，domain → api 契约的转换层。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GeneratorAssembler {

  /**
   * GenResultVO → CodeGenResultDTO。
   *
   * @param vo 生成结果 VO
   * @return Feign DTO
   */
  CodeGenResultDTO toGenResultDTO(GenResultVO vo);

  /**
   * CodePreviewVO → CodeGenResultDTO。
   *
   * @param vo 预览结果 VO
   * @return Feign DTO
   */
  CodeGenResultDTO toPreviewDTO(CodePreviewVO vo);

  /**
   * CodePreviewVO 列表 → CodeGenResultDTO 列表。
   *
   * @param list 预览列表
   * @return DTO 列表
   */
  List<CodeGenResultDTO> toPreviewDTOList(List<CodePreviewVO> list);

  // TableMeta conversion would be added when TableMetaVO is fully defined
  // TableMetaDTO toTableMetaDTO(TableMetaVO vo);
}
