package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContractTemplateMapper extends BaseMapper<ContractTemplateDO> {

    ContractTemplateDO selectByCode(@Param("code") String code);

    List<ContractTemplateDO> selectByType(@Param("contractType") String contractType,
                                          @Param("status") String status);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    long countByTypeAndStatus(@Param("contractType") String contractType,
                              @Param("status") String status);
}
