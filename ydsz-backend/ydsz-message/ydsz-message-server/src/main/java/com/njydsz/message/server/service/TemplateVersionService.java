package com.njydsz.message.server.service.template;

import java.util.List;

import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.template.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.template.TemplateTestSendDTO;
import com.njydsz.message.domain.entity.template.MsgTemplateVersionDO;

/**
 * 模板版本管理与可视化服务。
 *
 * <p>P1-6: 提供模板版本历史查询、版本回滚、模板预览（渲染参数）和模板试发功能。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface TemplateVersionService {

    /**
     * 查询模板版本历史列表。
     *
     * @param templateCode 模板编码
     * @return 版本列表（按版本号降序）
     */
    List<MsgTemplateVersionDO> listVersions(String templateCode);

    /**
     * 记录模板版本快照（审核通过/拒绝时调用）。
     *
     * @param templateCode 模板编码
     * @param content      模板内容快照
     * @param variableDefs 变量定义快照
     * @param auditStatus  审核状态
     * @param auditor      审核人
     * @param auditRemark  审核意见
     * @return 版本记录
     */
    MsgTemplateVersionDO recordVersion(String templateCode, String content, String variableDefs,
                                       String auditStatus, String auditor, String auditRemark);

    /**
     * 回滚到指定版本。
     *
     * @param templateCode 模板编码
     * @param version      目标版本号
     * @return 回滚后的模板内容
     */
    String rollbackToVersion(String templateCode, int version);

    /**
     * 预览模板渲染结果（不实际发送）。
     *
     * @param dto 预览请求
     * @return 渲染后的内容
     */
    String preview(TemplatePreviewDTO dto);

    /**
     * 试发模板（向测试接收人发送真实消息）。
     *
     * @param dto 试发请求
     * @return 发送结果
     */
    MessageResult testSend(TemplateTestSendDTO dto);
}
