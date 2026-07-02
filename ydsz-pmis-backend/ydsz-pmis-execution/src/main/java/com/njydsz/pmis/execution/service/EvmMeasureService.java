package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值测量服务
 *
 * <p>提供挣值测量数据的录入/更新（幂等）、偏差趋势及驾驶舱健康度查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface EvmMeasureService {

    /**
     * 录入或更新 EVM 测量（按 initiation+wbs+period 唯一）
     *
     * @param dto 测量录入参数
     * @return 测量记录 ID
     */
    Long save(EvmMeasureCreateDTO dto);

    /**
     * 根据ID查询测量记录
     *
     * @param id 记录ID
     * @return 测量实体
     */
    EvmMeasureDO getById(Long id);

    /**
     * 查询项目下所有测量记录
     *
     * @param initiationId 项目立项ID
     * @return 测量列表
     */
    List<EvmMeasureDO> listByInitiation(Long initiationId);

    /**
     * 查询 WBS 节点下所有测量记录
     *
     * @param wbsTaskId WBS任务ID
     * @return 测量列表
     */
    List<EvmMeasureDO> listByWbs(Long wbsTaskId);

    /**
     * WBS 节点级偏差趋势
     *
     * @param initiationId 项目立项 ID
     * @return 偏差趋势列表
     */
    List<Map<String, Object>> trend(Long initiationId);

    /**
     * 项目 EVM 健康汇总（最新一期）
     *
     * @param initiationId 项目立项 ID
     * @return EVM 健康汇总数据
     */
    Map<String, Object> dashboard(Long initiationId);

    /**
     * 分页查询测量记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项ID
     * @param alertLevel   告警级别
     * @return 分页结果
     */
    Page<EvmMeasureDO> page(int page, int size, Long initiationId, String alertLevel);

    /**
     * 删除测量记录
     *
     * @param id 记录ID
     */
    void delete(Long id);

    /**
     * 项目变更触发的 EVM 基线重算
     *
     * <p>由 ProjectChangeExecutedEvent 监听器调用, 根据最新 BAC/工期/范围,
     * 标记该项目 EVM 待重算并刷新基线版本号, 后续新录入的测量自动使用新基线.
     *
     * @param initiationId 项目立项 ID
     * @param reason       重算原因 (如 "PROJECT_CHANGE: changeCode")
     * @return 重算结果 (baselineVersion / affectedMeasures)
     */
    Map<String, Object> recalculateBaseline(Long initiationId, String reason);

    /**
     * 查询项目当前 EVM 基线版本号, 不存在返回 0
     *
     * @param initiationId 项目立项 ID
     * @return 基线版本号
     */
    int currentBaselineVersion(Long initiationId);
}
