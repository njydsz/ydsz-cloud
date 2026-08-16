package com.njydsz.message.server.service.template;

import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.template.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.template.TemplateTestSendDTO;
import com.njydsz.message.domain.entity.template.MsgTemplateVersion;
import java.util.List;

/**
 * 消息模板版本管理 Service
 *
 * <p>提供模板的版本快照、回滚、可视化预览、试发能力。模板每次审核通过时记录一份版本快照 写入 {@code ydsz_msg_template_version},可随时回滚到任意历史版本。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本历史</b>：{@link #listVersions} — 按模板编码查询版本列表(降序)
 *   <li><b>版本快照</b>：{@link #recordVersion} — 审核通过/拒绝时记录当前快照
 *   <li><b>版本回滚</b>：{@link #rollbackToVersion} — 回滚到指定版本号的内容
 *   <li><b>预览</b>：{@link #preview} — 渲染模板(不实际发送)
 *   <li><b>试发</b>：{@link #testSend} — 真实发送一条给测试接收人
 * </ul>
 *
 * <p><b>对标钉钉/飞书开放平台：</b>提供与「消息模板预览 / 试发」相同的能力,方便模板编辑后即时验证效果。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.template.MsgTemplateVersion 模板版本实体
 * @see TemplateService 模板主 Service
 */
public interface TemplateVersionService {

  /**
   * 查询模板版本历史列表。
   *
   * @param templateCode 模板编码
   * @return 版本列表（按版本号降序）
   */
  List<MsgTemplateVersion> listVersions(String templateCode);

  /**
   * 记录模板版本快照（审核通过/拒绝时调用）。
   *
   * @param templateCode 模板编码
   * @param content 模板内容快照
   * @param variableDefs 变量定义快照
   * @param auditStatus 审核状态
   * @param auditor 审核人
   * @param auditRemark 审核意见
   * @return 版本记录
   */
  MsgTemplateVersion recordVersion(
      String templateCode,
      String content,
      String variableDefs,
      String auditStatus,
      String auditor,
      String auditRemark);

  /**
   * 回滚到指定版本。
   *
   * @param templateCode 模板编码
   * @param version 目标版本号
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
