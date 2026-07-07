package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.RevenueCreateDTO;
import com.njydsz.pmis.project.entity.RevenueDO;

import java.util.List;
import java.util.Map;

/**
 * 收入确认服务
 *
 * <p>提供收入录入、确认、冲红及按项目/合同/期间的聚合查询能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RevenueService {

    /**
     * 录入收入
     *
     * @param dto 收入创建参数
     * @return 收入记录ID
     */
    Long create(RevenueCreateDTO dto);

    /**
     * 确认收入
     *
     * @param id          收入记录ID
     * @param confirmedBy 确认人ID
     */
    void confirm(String id, String confirmedBy);

    /**
     * 冲红
     *
     * @param id 收入记录ID
     */
    void reverse(String id);

    /**
     * 删除收入记录
     *
     * @param id 收入记录ID
     */
    void delete(String id);

    /**
     * 根据ID查询收入记录
     *
     * @param id 收入记录ID
     * @return 收入实体
     */
    RevenueDO getById(String id);

    /**
     * 分页查询收入记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同ID
     * @param initiationId 项目立项ID
     * @param period       期间（YYYY-MM）
     * @return 分页结果
     */
    Page<RevenueDO> page(int page, int size, String keyword, String status,
                          Long contractId, Long initiationId, String period);

    /**
     * 查询项目下所有收入记录
     *
     * @param initiationId 项目立项ID
     * @return 收入列表
     */
    List<RevenueDO> listByInitiation(Long initiationId);

    /**
     * 按合同汇总
     */
    List<Map<String, Object>> sumByContract(Long contractId);

    /**
     * 按期间汇总
     */
    List<Map<String, Object>> sumByPeriod(Long initiationId);
}
