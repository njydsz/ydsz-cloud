package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.message.entity.MsgAggregateDO;

/**
 * 聚合批次服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AggregateService {

    /**
     * 追加消息到聚合批次,不存在则新建批次
     *
     * @param group    聚合组
     * @param receiver 接收人
     * @param channel  通道
     * @param tenantId 租户 ID
     * @return 聚合批次实体
     */
    MsgAggregateDO appendOrStart(String group, String receiver, String channel, String tenantId);

    /**
     * 刷新到期的聚合批次(发送摘要)
     *
     * @return 已发送批次数
     */
    int flushDue();

    /**
     * 按聚合组 + 接收人刷新批次
     *
     * @param group    聚合组
     * @param receiver 接收人
     * @return 已发送批次数
     */
    int flushByGroup(String group, String receiver);

    /**
     * 分页查询聚合批次
     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgAggregateDO> page(PageQuery query);
}
