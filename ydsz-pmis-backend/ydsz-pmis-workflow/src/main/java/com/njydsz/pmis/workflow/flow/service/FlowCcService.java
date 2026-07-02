package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.workflow.flow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowCcDO;

import java.util.List;

/**
 * 流程抄送服务
 *
 * <p>P0-3: 抄送中心（对标钉钉/飞书的"抄送我的"独立 Tab）。
 * <p>对外暴露：分页查询/未读数/已读标记；
 * <p>对内由 {@code DefaultFlowAdvancer} 在 CC 节点触发时写入。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface FlowCcService {

    /**
     * 触发抄送（由 CC 节点/人工/自动规则调用）
     *
     * @param cc 抄送记录
     * @return 写入的 ID
     */
    Long saveCc(FlowCcDO cc);

    /**
     * 批量抄送
     *
     * @param ccs 抄送记录列表
     * @return 写入条数
     */
    int saveCcBatch(List<FlowCcDO> ccs);

    /**
     * 抄送中心分页查询
     *
     * @param tenantId 租户 ID
     * @param userId   接收人
     * @param query    查询条件
     * @return 抄送记录分页
     */
    List<FlowCcDO> pageMyCc(Long tenantId, Long userId, FlowCcQueryDTO query);

    /**
     * 抄送中心总数
     */
    long countMyCc(Long tenantId, Long userId, FlowCcQueryDTO query);

    /**
     * 未读抄送数
     */
    long countUnread(Long tenantId, Long userId);

    /**
     * 标记已读
     */
    boolean markRead(Long tenantId, Long userId, Long id);

    /**
     * 全部标记已读
     */
    int markAllRead(Long tenantId, Long userId);
}
