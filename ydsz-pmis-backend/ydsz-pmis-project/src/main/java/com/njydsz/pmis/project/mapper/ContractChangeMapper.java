package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractChangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同变更记录数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ContractChangeMapper extends BaseMapper<ContractChangeDO> {

    /**
     * 根据合同 ID 查询变更记录列表。
     *
     * @param contractId 合同 ID
     * @return 变更记录列表
     */
    List<ContractChangeDO> selectByContractId(@Param("contractId") Long contractId);

    /**
     * 根据变更单号查询合同变更记录。
     *
     * @param code 变更单号
     * @return 变更记录；不存在返回 null
     */
    ContractChangeDO selectByCode(@Param("code") String code);

    /**
     * 更新变更状态与审批人信息。
     *
     * @param id           变更 ID
     * @param status       目标状态码
     * @param approverId   审批人 ID
     * @param approverName 审批人名称
     * @return 受影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId,
                     @Param("approverName") String approverName);
}
