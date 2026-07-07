package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.project.dto.SimulationStatusDTO;
import com.njydsz.pmis.project.entity.ProfitSimulationDO;

import java.util.List;
import java.util.Map;

/**
 * 利润测算服务
 *
 * <p>支持项目利润滚动预测、多版本对比（What-if 模拟）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ProfitSimulationService {

    /**
     * 创建测算版本（版本号自增）
     *
     * @param dto 测算创建参数
     * @return 测算版本ID
     */
    Long create(ProfitSimulationCreateDTO dto);

    /**
     * 状态迁移（DRAFT→SUBMITTED→APPROVED/REJECTED→ARCHIVED）
     *
     * @param dto 状态变更参数
     */
    void changeStatus(SimulationStatusDTO dto);

    /**
     * 删除测算版本（仅 DRAFT/SUBMITTED/REJECTED 可删）
     *
     * @param id 测算版本ID
     */
    void delete(String id);

    /**
     * 根据ID查询测算版本
     *
     * @param id 测算版本ID
     * @return 测算版本实体
     */
    ProfitSimulationDO getById(String id);

    /**
     * 查询项目下所有测算版本
     *
     * @param initiationId 项目立项ID
     * @return 测算版本列表
     */
    List<ProfitSimulationDO> listByInitiation(Long initiationId);

    /** 多版本对比 */
    List<Map<String, Object>> compare(Long initiationId);

    /**
     * 分页查询测算版本
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项ID
     * @param scenarioType 场景类型
     * @param status       状态过滤
     * @return 分页结果
     */
    Page<ProfitSimulationDO> page(int page, int size, Long initiationId, String scenarioType, String status);
}
