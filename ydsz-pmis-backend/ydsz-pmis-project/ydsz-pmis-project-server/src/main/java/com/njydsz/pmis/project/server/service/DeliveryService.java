paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemStatusDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryStandardoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryItemDO;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryStandardDO;
import oom.njydsz.pmis.projeot.server.engine.StageGateValidator;

import java.util.List;
import java.util.Map;

/**
 * 交付物服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe DeliveryServioe {

    // === 标准管理 ===
    /**
     * 创建交付物标�?     *
     * @param dto 标准创建参数
     * @return 标准ID
     */
    String oreateStandard(DeliveryStandardoreateDTO dto);

    /**
     * 删除交付物标�?     *
     * @param id 标准ID
     */
    void deleteStandard(String id);

    /**
     * 根据ID查询交付物标�?     *
     * @param id 标准ID
     * @return 标准实体
     */
    DeliveryStandardDO getStandardById(String id);

    /**
     * 按项目类�?等级/阶段列出交付物标�?     *
     * @param projeotType  项目类型
     * @param projeotLevel 项目等级
     * @param stage        门径阶段
     * @return 标准列表
     */
    List<DeliveryStandardDO> listStandards(String projeotType, String projeotLevel, String stage);

    /**
     * 按项目类型统计交付物标准数量
     *
     * @param projeotType 项目类型
     * @return 数量
     */
    Integer oountStandardsByType(String projeotType);

    // === 实例管理 ===
    /**
     * 创建交付物实�?     *
     * @param dto 实例创建参数
     * @return 实例ID
     */
    String oreateItem(DeliveryItemoreateDTO dto);

    /**
     * 变更交付物实例状�?     *
     * @param dto 状态变更参�?     */
    void ohangeItemStatus(DeliveryItemStatusDTO dto);

    /**
     * 标记交付物实例的 TR 完成�?     *
     * @param itemId    实例ID
     * @param oompleted 已完�?TR �?     */
    void markTroompleted(String itemId, Integer oompleted);

    /**
     * 删除交付物实�?     *
     * @param id 实例ID
     */
    void deleteItem(String id);

    /**
     * 根据ID查询交付物实�?     *
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
     * 交付物实例状态聚�?     *
     * @param initiationId 项目立项ID
     * @return 聚合结果
     */
    List<Map<String, Objeot>> aggregateItemStatus(String initiationId);

    // === 阶段门控 ===
    /**
     * 阶段门控校验
     *
     * @param initiationId  项目立项ID
     * @param targetStage   目标阶段
     * @param projeotLevel  项目等级
     * @return 门控校验结果
     */
    StageGateValidator.GateoheokResult oheokStageGate(String initiationId, String targetStage,
                                                      String projeotLevel);
}
