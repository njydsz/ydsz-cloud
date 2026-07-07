package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;

import java.util.List;

/**
 * 合同补充协议服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractSupplementService {

    /**
     * 创建合同补充协议。
     *
     * @param dto 补充协议参数
     * @return 补充协议 ID
     */
    String create(ContractSupplementDTO dto);

    /**
     * 删除补充协议（逻辑删除）。
     *
     * @param id 补充协议 ID
     */
    void delete(String id);

    /**
     * 根据补充协议 ID 查询详情。
     *
     * @param id 补充协议 ID
     * @return 补充协议实体；不存在返回 null
     */
    ContractSupplementDO getById(String id);

    /**
     * 按合同查询补充协议列表。
     *
     * @param contractId 合同 ID
     * @return 补充协议列表
     */
    List<ContractSupplementDO> listByContract(String contractId);

    /**
     * 分页查询补充协议。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param contractId 合同 ID，可空
     * @return 分页结果
     */
    Page<ContractSupplementDO> page(int page, int size, String contractId);
}
