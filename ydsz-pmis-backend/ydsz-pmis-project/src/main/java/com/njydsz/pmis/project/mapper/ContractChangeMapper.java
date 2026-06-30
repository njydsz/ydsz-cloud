package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractChangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContractChangeMapper extends BaseMapper<ContractChangeDO> {

    List<ContractChangeDO> selectByContractId(@Param("contractId") Long contractId);

    ContractChangeDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId,
                     @Param("approverName") String approverName);
}
