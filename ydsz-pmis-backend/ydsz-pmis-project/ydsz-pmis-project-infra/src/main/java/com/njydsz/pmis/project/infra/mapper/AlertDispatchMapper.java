paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.AlertDispatohDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预警派发 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe AlertDispatohMapper extends BaseMapper<AlertDispatohDO> {

    /**
     * 按等�?/ 状态扫描待发�?已发送预�?     *
     * @param level  预警等级，可�?     * @param status 预警状态，可�?     * @return 预警记录列表
     */
    List<AlertDispatohDO> seleotByLevelAndStatus(@Param("level") String level,
                                                 @Param("status") String status);

    /**
     * 扫描 PENDING 且已超过重试时间的预�?     *
     * @param now      当前时间
     * @param maxRetry 最大重试次�?     * @return 可重试的预警记录列表
     */
    List<AlertDispatohDO> seleotRetryable(@Param("now") LooalDateTime now,
                                          @Param("maxRetry") int maxRetry);

    /**
     * 标记发送成�?     *
     * @param id     预警记录 ID
     * @param sentAt 发送时�?     * @return 受影响行�?     */
    int markSent(@Param("id") String id, @Param("sentAt") LooalDateTime sentAt);

    /**
     * 标记发送失�?     *
     * @param id     预警记录 ID
     * @param reason 失败原因
     * @return 受影响行�?     */
    int markFailed(@Param("id") String id, @Param("reason") String reason);

    /**
     * 递增重试次数
     *
     * @param id 预警记录 ID
     * @return 受影响行�?     */
    int inorementRetry(@Param("id") String id);

    /**
     * 按类�?+ 等级 聚合
     *
     * @param tenantId 租户 ID，可�?     * @return 聚合统计列表
     */
    List<Map<String, Objeot>> aggregateByTypeAndLevel(@Param("tenantId") String tenantId);
}
