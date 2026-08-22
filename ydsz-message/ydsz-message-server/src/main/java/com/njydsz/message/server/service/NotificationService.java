package com.njydsz.message.server.service.core;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.domain.vo.NotificationGroupVO;

/**
 * 站内通知 Service 接口
 *
 * <p>提供站内消息的发送、收件箱查询、未读统计、已读标记、撤回等核心能力。 站内通知是消息中心（ydsz-message）的"应用内"渠道，不依赖第三方短信/邮件/推送。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>发送</b>：{@link #send} — 单发/群发（多个 receiverId）/广播（全员）
 *   <li><b>查询</b>：分页收件箱（{@link #inbox}）、分组折叠（{@link #inboxGrouped}）
 *   <li><b>状态</b>：未读数（{@link #countUnread}）/ 单条已读（{@link #markRead}）/ 全部已读（{@link #markAllRead}）
 *   <li><b>管理</b>：逻辑删除（{@link #delete}）/ 撤回（{@link #recall}）
 * </ul>
 *
 * <p><b>应用内推送：</b>用户登录时，WebSocket 通道会订阅用户收件箱，新通知通过 {@code WebSocketPushService} 实时推送到前端。
 *
 * <p><b>事务：</b>所有写操作（{@code send/markRead/markAllRead/delete/recall}） 开启
 * {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.MessageService 全渠道消息（含短信/邮件/推送）
 * @see MsgNotificationVO 站内通知实体
 */
public interface NotificationService {

  /**
   * 发送站内通知（单发或群发）
   *
   * <p>支持三种模式：① 单发（{@code receiverIds} 含 1 个用户）；② 群发（多个用户）； ③ 广播（{@code receiverIds=null} 且 {@code
   * broadcast=true}，发送给全租户所有用户）。 发送后立即推送 WebSocket 消息到在线用户。
   *
   * @param dto 发送参数（title / content / priority / messageGroup / receiverIds / businessType）
   * @return 实际发送的通知条数
   */
  int send(NotificationSendDTO dto);

  /**
   * 分页查询用户收件箱
   *
   * <p>按 {@code read=false} 优先 + 创建时间倒序排列；支持按 {@code businessType} 和 {@code messageGroup} 过滤。走
   * {@code idx_user_read} 复合索引。
   *
   * @param userId 用户 ID
   * @param query 查询条件（pageNum / pageSize / read / businessType / messageGroup）
   * @return 分页结果（含 total / records）
   */
  Page<MsgNotificationVO> inbox(String userId, NotificationQueryDTO query);

  /**
   * 统计用户未读通知数
   *
   * <p>走 Redis 缓存（{@code ydsz:msg:notify:unread:{userId}}），TTL 5min； 新通知发送时主动失效。
   *
   * @param userId 用户 ID
   * @return 未读通知条数
   */
  long countUnread(String userId);

  /**
   * 标记单条通知为已读
   *
   * <p>仅通知的接收人可以标记；非接收人操作会被静默忽略（避免越权）。
   *
   * @param userId 当前用户 ID（必须为通知的 receiverId）
   * @param id 通知 ID
   * @return true=标记成功，false=通知不存在或非接收人
   */
  boolean markRead(String userId, String id);

  /**
   * 标记当前用户全部未读通知为已读
   *
   * <p>使用单条 UPDATE ... WHERE user_id=? AND read=false 批量更新，避免 N+1。 完成后失效该用户的未读数缓存。
   *
   * @param userId 用户 ID
   * @return 受影响的行数
   */
  int markAllRead(String userId);

  /**
   * 删除指定通知（逻辑删除）
   *
   * <p>仅通知的接收人可以删除（防止越权）。已删除的通知不可恢复，但已读状态保留。
   *
   * @param userId 当前用户 ID
   * @param ids 通知 ID 列表
   */
  void delete(String userId, List<String> ids);

  /**
   * 撤回单条通知
   *
   * <p>对标 IM 的"撤回消息"能力。撤回后：① 通知内容置为"[已撤回]"占位； ② 推送 WebSocket 撤回事件给在线用户；③ 写审计日志。 限制：发出超过 2
   * 分钟的通知不允许撤回。
   *
   * @param userId 当前用户 ID（仅发送者可撤回）
   * @param id 通知 ID
   * @return true=撤回成功，false=超时或非发送者
   */
  boolean recall(String userId, String id);

  /**
   * P1-2: 分组查询收件箱（按 message_group 折叠，每组返回最新一条+未读数）。
   *
   * <p>适用于"会话式"通知收件箱（如订单状态变更、审批结果通知按业务单号分组）。 避免同业务单号的多条通知把列表刷屏。
   *
   * @param userId 用户 ID
   * @param query 查询参数
   * @return 分组列表（每组：最新一条通知 + 该分组总未读数 + 通知数）
   */
  Page<NotificationGroupVO> inboxGrouped(String userId, NotificationQueryDTO query);

  /**
   * P1-2: 查询指定分组的所有通知。
   *
   * <p>点击分组项后调用此接口展开全部通知。
   *
   * @param userId 用户 ID
   * @param messageGroup 分组键（通常为业务单号）
   * @return 该分组下的全部通知（按创建时间倒序）
   */
  List<MsgNotificationVO> listByGroup(String userId, String messageGroup);
}
