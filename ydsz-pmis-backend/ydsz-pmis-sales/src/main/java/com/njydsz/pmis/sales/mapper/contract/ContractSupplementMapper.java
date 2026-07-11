package com.njydsz.pmis.sales.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.sales.entity.contract.ContractSupplementDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同补充协议数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ContractSupplementMapper extends BaseMapper<ContractSupplementDO> {

    /**
     * 根据合同 ID 查询补充协议列表。
     *
     * @param contractId 合同 ID
     * @return 补充协议列表
     */
    List<ContractSupplementDO> selectByContractId(@Param("contractId") String contractId);

    /**
     * 根据补充协议编号查询记录。
     *
     * @param code 补充协议编号
     * @return 补充协议；不存在返回 null
     */
    ContractSupplementDO selectByCode(@Param("code") String code);
}
