package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同模板数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ContractTemplateMapper extends BaseMapper<ContractTemplateDO> {

    /**
     * 根据模板编码查询合同模板。
     *
     * @param code 模板编码（业务唯一）
     * @return 合同模板；不存在返回 null
     */
    ContractTemplateDO selectByCode(@Param("code") String code);

    /**
     * 根据合同类型与状态查询模板列表。
     *
     * @param contractType 合同类型（ContractTemplateType.code）
     * @param status       模板状态（ContractTemplateStatus.code）
     * @return 模板列表
     */
    List<ContractTemplateDO> selectByType(@Param("contractType") String contractType,
                                          @Param("status") String status);

    /**
     * 更新模板状态（DRAFT/PUBLISHED/DEPRECATED 之间转换）。
     *
     * @param id     模板 ID
     * @param status 目标状态码
     * @return 受影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 统计指定类型与状态的模板数量。
     *
     * @param contractType 合同类型
     * @param status       模板状态
     * @return 数量
     */
    long countByTypeAndStatus(@Param("contractType") String contractType,
                              @Param("status") String status);
}
