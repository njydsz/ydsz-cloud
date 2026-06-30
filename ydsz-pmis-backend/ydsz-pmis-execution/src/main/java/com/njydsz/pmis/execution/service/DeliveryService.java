package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.execution.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.execution.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import com.njydsz.pmis.execution.entity.DeliveryStandardDO;
import com.njydsz.pmis.execution.engine.StageGateValidator;

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
    Long createStandard(DeliveryStandardCreateDTO dto);

    void deleteStandard(Long id);

    DeliveryStandardDO getStandardById(Long id);

    List<DeliveryStandardDO> listStandards(String projectType, String projectLevel, String stage);

    long countStandardsByType(String projectType);

    // === 实例管理 ===
    Long createItem(DeliveryItemCreateDTO dto);

    void changeItemStatus(DeliveryItemStatusDTO dto);

    void markTrCompleted(Long itemId, Integer completed);

    void deleteItem(Long id);

    DeliveryItemDO getItemById(Long id);

    List<DeliveryItemDO> listItemsByInitiation(Long initiationId);

    List<DeliveryItemDO> listItemsByStage(Long initiationId, String stage);

    List<Map<String, Object>> aggregateItemStatus(Long initiationId);

    // === 阶段门控 ===
    StageGateValidator.GateCheckResult checkStageGate(Long initiationId, String targetStage,
                                                      String projectLevel);
}
