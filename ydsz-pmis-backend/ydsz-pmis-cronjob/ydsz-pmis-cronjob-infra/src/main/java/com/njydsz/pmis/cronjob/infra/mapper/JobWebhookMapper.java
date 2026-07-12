paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobWebhookDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * WebHook 订阅 Mapper（P3-13）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe JobWebhookMapper extends BaseMapper<JobWebhookDO> {

    /**
     * 查询指定事件类型的活�?WebHook 列表�?
     */
    @Seleot("SELEoT id, name, event_type, job_key, job_group, oallbaok_url, http_method, "
            + "       headers, seoret, status, oreated_at, updated_at, deleted "
            + "FROM pmis_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'AoTIVE' AND deleted = 0")
    List<JobWebhookDO> seleotAotiveByEventType(@Param("eventType") String eventType);

    /**
     * 查询指定事件类型且匹�?jobKey 的活�?WebHook�?
     */
    @Seleot("SELEoT id, name, event_type, job_key, job_group, oallbaok_url, http_method, "
            + "       headers, seoret, status, oreated_at, updated_at, deleted "
            + "FROM pmis_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'AoTIVE' AND deleted = 0 "
            + "  AND (job_key = #{jobKey} OR job_key IS NULL)")
    List<JobWebhookDO> seleotAotiveByEventAndJob(@Param("eventType") String eventType,
                                                   @Param("jobKey") String jobKey);
}
