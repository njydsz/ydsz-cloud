package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractSupplementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContractSupplementMapper extends BaseMapper<ContractSupplementDO> {

    List<ContractSupplementDO> selectByContractId(@Param("contractId") Long contractId);

    ContractSupplementDO selectByCode(@Param("code") String code);
}
