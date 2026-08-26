package com.njydsz.message.server.service.template;

import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.message.domain.dto.TemplateAuditDTO;
import com.njydsz.message.domain.dto.TemplateCreateDTO;
import com.njydsz.message.domain.dto.TemplateQueryDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;

/**
 * 消息模板 Service 接口
 *
 * <p>提供消息模板（{@code ydsz_msg_template}）的完整管理能力：CRUD、版本管理、 模板审核、模板预览、变量渲染。模板是消息发送的"内容模板"，通过 {@code
 * ${var}} 占位符实现动态内容替换。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #create} / {@link #update} / {@link #delete} / {@link #getById} / {@link
 *       #page}
 *   <li><b>加载渲染</b>：{@link #loadByCodeAndChannel} — 发送时按 (code, channel, locale) 三元组加载
 *   <li><b>审核流</b>：{@link #audit} — 模板上线前需通过审核（避免乱发）
 *   <li><b>预览</b>：{@link #preview} — 渲染模板但不发送，供开发调试
 * </ul>
 *
 * <p><b>多语言回退：</b>{@link #loadByCodeAndChannel} 在 locale 为空时回退到默认语言（{@code zh-CN}）， 保证总有可用模板。
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/delete/audit}）开启 {@code @Transactional(rollbackFor =
 * Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MsgTemplateVO 模板实体
 * @see com.njydsz.message.server.service.TemplateVersionService 模板版本 Service
 */
public interface TemplateService {

  /**
   * 创建模板（默认状态 DRAFT 草稿）
   *
   * <p>创建后需通过 {@link #audit} 审核上线。模板编码（{@code templateCode}）全局唯一。
   *
   * @param dto 模板创建参数（templateCode / channel / locale / subject / content / vars）
   * @return 已创建的模板实体
   */
  MsgTemplateVO create(TemplateCreateDTO dto);

  /**
   * 更新模板（仅更新 DRAFT 状态的模板）
   *
   * <p>已审核（{@code APPROVED}）的模板不允许直接修改，请使用版本管理能力。
   *
   * @param id 模板 ID
   * @param dto 模板更新参数
   * @return 更新后的模板实体
   */
  MsgTemplateVO update(String id, TemplateCreateDTO dto);

  /**
   * 删除模板（逻辑删除）
   *
   * <p>已被业务引用的模板<b>禁止删除</b>（避免历史消息无模板可查）。 由 Service 层在删除前校验。
   *
   * @param id 模板 ID
   */
  void delete(String id);

  /**
   * 根据 ID 查询模板
   *
   * @param id 模板 ID
   * @return 模板实体；不存在时返回 null
   */
  MsgTemplateVO getById(String id);

  /**
   * 分页查询模板列表
   *
   * <p>支持按 {@code templateCode / channel / locale / status} 多条件过滤。
   *
   * @param query 查询参数
   * @return 分页结果（total / records）
   */
  Page<MsgTemplateVO> page(TemplateQueryDTO query);

  /**
   * 按编码 + 通道 + 语言加载模板（locale 为空时回退默认 zh-CN）
   *
   * <p>消息发送时的核心反查接口，按 (templateCode, channel, locale) 三元组定位模板内容。 多语言回退：locale=en-US 未命中时回退到
   * locale=zh-CN。
   *
   * @param templateCode 模板编码
   * @param channel 通道（IN_APP / SMS / EMAIL / PUSH / IM）
   * @param locale 语言区域（可为空）
   * @param tenantId 租户 ID
   * @return 模板实体；未匹配到时返回 null（不会抛异常）
   */
  MsgTemplateVO loadByCodeAndChannel(
      String templateCode, String channel, String locale, String tenantId);

  /**
   * 审核模板
   *
   * <p>模板上线前必须通过审核（{@code approve=true}）。审核通过后模板状态变为 {@code APPROVED}， 可被 {@link
   * #loadByCodeAndChannel} 加载。
   *
   * @param id 模板 ID
   * @param dto 审核参数（approve / auditComment / auditorId）
   */
  void audit(String id, TemplateAuditDTO dto);

  /**
   * GAP-3: 模板预览 — 渲染模板但不发送，供开发调试使用。
   *
   * <p>模板预览 API，传入模板编码 + 参数， 返回渲染后的内容预览，不落库不发送。
   *
   * @param templateCode 模板编码
   * @param channel 通道
   * @param params 模板参数（用于 ${var} 替换）
   * @param locale 语言区域（可为空）
   * @param tenantId 租户 ID
   * @return 渲染后的内容
   */
  String preview(
      String templateCode,
      String channel,
      Map<String, Object> params,
      String locale,
      String tenantId);
}
