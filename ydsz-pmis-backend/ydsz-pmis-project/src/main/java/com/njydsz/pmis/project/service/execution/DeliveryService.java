package com.njydsz.pmis.project.service.execution;

import com.njydsz.pmis.project.dto.execution.DeliveryItemCreateDTO;
import com.njydsz.pmis.project.dto.execution.DeliveryItemStatusDTO;
import com.njydsz.pmis.project.dto.execution.DeliveryStandardCreateDTO;
import com.njydsz.pmis.project.entity.execution.DeliveryItemDO;
import com.njydsz.pmis.project.entity.execution.DeliveryStandardDO;
import com.njydsz.pmis.project.engine.StageGateValidator;

import java.util.List;
import java.util.Map;

/**
 * 交付物服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface DeliveryService {

    // === 标准管理 ===
    /**
     * 创建交付物标准
     *
     * @param dto 标准创建参数
     * @return 标准ID
     */
    String createStandard(DeliveryStandardCreateDTO dto);

    /**
     * 删除交付物标准
     *
     * @param id 标准ID
     */
    void deleteStandard(String id);

    /**
     * 根据ID查询交付物标准
     *
     * @param id 标准ID
     * @return 标准实体
     */
    DeliveryStandardDO getStandardById(String id);

    /**
     * 按项目类型/等级/阶段列出交付物标准
     *
     * @param projectType  项目类型
     * @param projectLevel 项目等级
     * @param stage        门径阶段
     * @return 标准列表
     */
    List<DeliveryStandardDO> listStandards(String projectType, String projectLevel, String stage);

    /**
     * 按项目类型统计交付物标准数量
     *
     * @param projectType 项目类型
     * @return 数量
     */
    Integer countStandardsByType(String projectType);

    // === 实例管理 ===
    /**
     * 创建交付物实例
     *
     * @param dto 实例创建参数
     * @return 实例ID
     */
    String createItem(DeliveryItemCreateDTO dto);

    /**
     * 变更交付物实例状态
     *
     * @param dto 状态变更参数
     */
    void changeItemStatus(DeliveryItemStatusDTO dto);

    /**
     * 标记交付物实例的 TR 完成数
     *
     * @param itemId    实例ID
     * @param completed 已完成 TR 数
     */
    void markTrCompleted(String itemId, Integer completed);

    /**
     * 删除交付物实例
     *
     * @param id 实例ID
     */
    void deleteItem(String id);

    /**
     * 根据ID查询交付物实例
     *
     * @param id 实例ID
     * @return 实例实体
     */
    DeliveryItemDO getItemById(String id);

    /**
     * 查询项目下所有交付物实例
     *
     * @param initiationId 项目立项ID
     * @return 实例列表
     */
    List<DeliveryItemDO> listItemsByInitiation(String initiationId);

    /**
     * 按阶段查询交付物实例
     *
     * @param initiationId 项目立项ID
     * @param stage        门径阶段
     * @return 实例列表
     */
    List<DeliveryItemDO> listItemsByStage(String initiationId, String stage);

    /**
     * 交付物实例状态聚合
     *
     * @param initiationId 项目立项ID
     * @return 聚合结果
     */
    List<Map<String, Object>> aggregateItemStatus(String initiationId);

    // === 阶段门控 ===
    /**
     * 阶段门控校验
     *
     * @param initiationId  项目立项ID
     * @param targetStage   目标阶段
     * @param projectLevel  项目等级
     * @return 门控校验结果
     */
    StageGateValidator.GateCheckResult checkStageGate(String initiationId, String targetStage,
                                                      String projectLevel);
}
