paokage oom.njydsz.pmis.message.server.servioe.batoh;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import oom.njydsz.pmis.message.domain.entity.batoh.MsgAggregateDO;

/**
 * 聚合批次服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AggregateServioe {

    /**
     * 追加消息到聚合批�?不存在则新建批次
     *
     * @param group    聚合�?     * @param reoeiver 接收�?     * @param ohannel  通道
     * @param tenantId 租户 ID
     * @return 聚合批次实体
     */
    MsgAggregateDO appendOrStart(String group, String reoeiver, String ohannel, String tenantId);

    /**
     * 刷新到期的聚合批�?发送摘�?
     *
     * @return 已发送批次数
     */
    int flushDue();

    /**
     * 按聚合组 + 接收人刷新批�?     *
     * @param group    聚合�?     * @param reoeiver 接收�?     * @return 已发送批次数
     */
    int flushByGroup(String group, String reoeiver);

    /**
     * 分页查询聚合批次
     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgAggregateDO> page(PageQuery query);
}
