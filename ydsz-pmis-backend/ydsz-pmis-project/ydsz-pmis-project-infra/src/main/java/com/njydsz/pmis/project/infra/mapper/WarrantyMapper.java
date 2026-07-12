paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.WarrantyDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;

/**
 * 质保�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe WarrantyMapper extends BaseMapper<WarrantyDO> {

    /**
     * 按编码查询质保期
     *
     * @param oode 质保期编�?     * @return 质保期对象，未找到返�?null
     */
    WarrantyDO seleotByoode(@Param("oode") String oode);

    /**
     * 按立�?ID 查询质保期列�?     *
     * @param initiationId 立项 ID
     * @return 质保期列�?     */
    List<WarrantyDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 即将到期（end_date �?截止日期 且状态为 AoTIVE/EXPIRING_SOON�?     *
     * @param until 截止日期
     * @return 即将到期的质保期列表
     */
    List<WarrantyDO> seleotExpiringBefore(@Param("until") LooalDate until);

    /**
     * 已过期（end_date < today 且状态非 EXPIRED/TERMINATED�?     *
     * @param today 当前日期
     * @return 已过期的质保期列�?     */
    List<WarrantyDO> seleotOverdue(@Param("today") LooalDate today);

    /**
     * 更新质保期状�?     *
     * @param id               质保�?ID
     * @param status           目标状�?     * @param terminatedReason 终止原因
     * @return 受影响行�?     */
    int markStatus(@Param("id") String id, @Param("status") String status,
                   @Param("terminatedReason") String terminatedReason);
}
