#!/usr/bin/env python3
"""
批量修复 ydzs-message 模块 log 硬编码中文。

运行：
  python refactor_message_logs.py            # dry-run (仅预览)
  REFACTOR_LIVE=1 python refactor_message_logs.py   # 实际改写文件
"""
from __future__ import annotations

import os
import re
import sys
import hashlib
import datetime as _dt
import shutil
from pathlib import Path
from collections import defaultdict

ROOT = Path(r"D:/Code/open/ydsz-cloud/ydsz-message")
PROPS = {
    "default": ROOT / r"ydsz-message-server/src/main/resources/i18n/message-messages.properties",
    "en": ROOT / r"ydsz-message-server/src/main/resources/i18n/message-messages_en_US.properties",
    "zh": ROOT / r"ydsz-message-server/src/main/resources/i18n/message-messages_zh_CN.properties",
    "tw": ROOT / r"ydsz-message-server/src/main/resources/i18n/message-messages_zh_TW.properties",
}
SKIP_FILES = {"TcpPushChannel.java"}
BACKUP_BASE = Path(r"D:/Code/open/ydsz-cloud/data/scripts/backup_logs")

CJK = re.compile(r"[\u4e00-\u9fff]")
TAG_RE = re.compile(r"^\[([A-Za-z0-9_]+)\]\s*(.*)$", re.DOTALL)
IMPORT_LINE = "import com.njydsz.common.locales.util.I18n;"


# ----------------------------------------------------------------------------
# Manual i18n entries.  Key -> {"zh", "en", "tw", "default"}.
# Each entry is tried against literal zh via EXACT match on either the full literal or
# via a "slug + body" match.
# ----------------------------------------------------------------------------
T: dict[str, dict[str, str]] = {}


def e(key: str, zh: str, en: str, tw: str | None = None, default: str | None = None) -> None:
    T[key] = {"zh": zh, "en": en, "tw": tw or zh, "default": default or en}


# --- Frequently-occurring literal -> i18n key map (zh msg is unique) ---
L2K: dict[str, str] = {}


def _reg(zh: str, en: str, base_key: str, tw: str | None = None, default: str | None = None) -> None:
    e(base_key, zh, en, tw, default)
    L2K[zh] = base_key


# Channel base messages
_reg("服务正在关闭，拒绝新消息",
     "Service is shutting down, rejecting new messages",
     "message.log.server.messageconsumer.warn.service.shutting")
_reg("空消息体，跳过",
     "Empty message body, skipping",
     "message.log.server.messageconsumer.warn.empty.body")
_reg("BloomFilter 判定异常，降级放行: messageId={} err={}",
     "BloomFilter judgement exception, degrading: messageId={} err={}",
     "message.log.server.bloomfilter.warn.judge.exception.degrading")
_reg("BloomFilter 异常，降级放行: messageId={} err={}",
     "BloomFilter exception, degrading: messageId={} err={}",
     "message.log.server.bloomfilter.warn.exception.degrading")
_reg("DB 二级幂等检查命中，跳过: messageId={}",
     "DB secondary idempotent check hit, skipping: messageId={}",
     "message.log.server.messageconsumer.warn.db.idempotent.hit")
_reg("重复消息已跳过: key={} messageId={}",
     "Duplicate message skipped: key={} messageId={}",
     "message.log.server.messageconsumer.info.duplicate.skipped")
_reg("消费完成: messageId={} channel={} cost={}ms",
     "Message consumed: messageId={} channel={} cost={}ms",
     "message.log.server.messageconsumer.info.consumed")
_reg("业务异常: messageId={} err={}",
     "Business exception: messageId={} err={}",
     "message.log.server.messageconsumer.error.business.exception")
_reg("系统异常: messageId={}",
     "System exception: messageId={}",
     "message.log.server.messageconsumer.error.system.exception")
_reg("释放幂等锁失败(等待 TTL 过期): key={} err={}",
     "Failed to release idempotent lock (waiting TTL expiry): key={} err={}",
     "message.log.server.messageconsumer.warn.release.lock.failed")
_reg("已更新现有记录为 FAILED: messageId={}",
     "Updated existing record to FAILED: messageId={}",
     "message.log.server.messageconsumer.info.updated.failed.record")
_reg("记录失败日志异常: messageId={} err={}",
     "Record log exception: messageId={} err={}",
     "message.log.server.messageconsumer.error.record.failed.exception")
_reg("重复消息已跳过：key={} messageId={}",
     "Duplicate message skipped: key={} messageId={}",
     "message.log.server.messageconsumer.info.duplicate.skipped.cn")
_reg("消费完成：messageId={} channel={} 耗时 {cost}ms",
     "Message consumed: messageId={} channel={} cost={cost}ms",
     "message.log.server.messageconsumer.info.consumed.cn")
_reg("业务异常：messageId={} err={}",
     "Business exception: messageId={} err={}",
     "message.log.server.messageconsumer.error.business.exception.cn")
_reg("系统异常：messageId={}",
     "System exception: messageId={}",
     "message.log.server.messageconsumer.error.system.exception.cn")
_reg("开始优雅停机... inFlight={}",
     "Starting graceful shutdown... inFlight={}",
     "message.log.server.messageconsumer.info.graceful.shutdown.start")
_reg("优雅停机超时，仍有 {} 条消息在处理中",
     "Graceful shutdown timed out, still {} messages in flight",
     "message.log.server.messageconsumer.warn.graceful.shutdown.timeout")
_reg("优雅停机完成，所有消息已处理",
     "Graceful shutdown complete, all messages processed",
     "message.log.server.messageconsumer.info.graceful.shutdown.complete")
_reg("消息已过期，跳过: messageId={} channel={}",
     "Message expired, skipping: messageId={} channel={}",
     "message.log.server.messageconsumer.warn.message.expired")
_reg("BloomFilter 疑似重复，跳过: messageId={}",
     "BloomFilter suspect duplicate, skipping: messageId={}",
     "message.log.server.messageconsumer.info.bloomfilter.duplicate")
_reg("解析失败: body={} err={}",
     "Parse failed: body={} err={}",
     "message.log.server.messageconsumer.error.parse.failed")
_reg("DB二级幂等检查命中，跳过: messageId={}",
     "DB secondary idempotent check hit, skipping: messageId={}",
     "message.log.server.messageconsumer.warn.db.idempotent.hit.raw")
_reg("释放幂等锁失败(等待 TTL 过期): key={} err={}",
     "Failed to release idempotent lock (waiting TTL expiry): key={} err={}",
     "message.log.server.messageconsumer.warn.release.lock.failed.raw")
_reg("释放幂等锁失败（等待 TTL 过期）：key={} err={}",
     "Failed to release idempotent lock (waiting TTL expiry): key={} err={}",
     "message.log.server.messageconsumer.warn.release.lock.failed.cn")
_reg("记录失败日志异常：messageId={} err={}",
     "Record log exception: messageId={} err={}",
     "message.log.server.messageconsumer.error.record.failed.exception.cn")

# BatchMessageConsumer
_reg("空消息体,跳过",
     "Empty message body, skipping",
     "message.log.server.batchconsumer.warn.empty.body")
_reg("空消息体，跳过",
     "Empty message body, skipping",
     "message.log.server.batchconsumer.warn.empty.body.cn")
_reg("批量消息解析失败,尝试单条解析: err={}",
     "Batch message parse failed, trying single parse: err={}",
     "message.log.server.batchconsumer.error.parse.failed.try.single")
_reg("批量消息解析失败，尝试单条解析：err={}",
     "Batch message parse failed, trying single parse: err={}",
     "message.log.server.batchconsumer.error.parse.failed.try.single.cn")
_reg("单条解析也失败: {}",
     "Single message parse also failed: {}",
     "message.log.server.batchconsumer.error.single.parse.failed")
_reg("收到批量消息: count={}",
     "Received batch messages: count={}",
     "message.log.server.batchconsumer.info.received")
_reg("批量内消息已处理,跳过: msgId={}",
     "Batch message already processed, skipping: msgId={}",
     "message.log.server.batchconsumer.info.batch.already.processed")
_reg("批量内消息已处理，跳过：msgId={}",
     "Batch message already processed, skipping: msgId={}",
     "message.log.server.batchconsumer.info.batch.already.processed.cn")

# ChannelRouter
_reg("跳过 channelType 为空的通道: {}",
     "Skipping channel with empty channelType: {}",
     "message.log.server.channelrouter.warn.skip.empty.channeltype")
_reg("已注册 {} 个消息通道(含熔断器): {}",
     "Registered {} message channels (incl. circuit breaker): {}",
     "message.log.server.channelrouter.info.registered.count")
_reg("通道熔断中，快速失败: channel={} state={}",
     "Channel circuit broken, fast failing: channel={} state={}",
     "message.log.server.channelrouter.warn.breaker.fast.fail")
_reg("无可用启用通道",
     "No available enabled channels",
     "message.log.server.channelrouter.warn.no.enabled.channels")
_reg("通道评分排序={}",
     "Channel scoring sort: {}",
     "message.log.server.channelrouter.info.dispatch.score")
_reg("通道发送成功 channel={} totalRate={}",
     "Channel send success: channel={} totalRate={}",
     "message.log.server.channelrouter.info.dispatch.send.success")
_reg("通道发送失败尝试下一个 channel={} err={}",
     "Channel send failed, trying next: channel={} err={}",
     "message.log.server.channelrouter.warn.dispatch.send.failed.try.next")
_reg("所有通道均失败，lastError={}",
     "All channels failed, lastError={}",
     "message.log.server.channelrouter.error.dispatch.all.failed")
_reg("channel={} 发送异常 costMs={} cbState={}",
     "channel={} send exception costMs={} cbState={}",
     "message.log.server.channelrouter.error.exception")
_reg("templateParams 解析失败，忽略: msgId={}, err={}",
     "templateParams parse failed, ignoring: msgId={}, err={}",
     "message.log.server.channelrouter.warn.template.parse.failed.ignoring")
_reg("templateParams 解析失败,忽略: msgId={}, err={}",
     "templateParams parse failed, ignoring: msgId={}, err={}",
     "message.log.server.channelrouter.warn.template.parse.failed.ignoring.raw")
_reg("dispatchWithScore: 无可用启用通道",
     "dispatchWithScore: no available enabled channels",
     "message.log.server.channelrouter.warn.dispatch.no.enabled.channels")
_reg("dispatchWithScore: 通道评分排序={}",
     "dispatchWithScore: channel scoring sort: {}",
     "message.log.server.channelrouter.info.dispatch.score.cn")
_reg("dispatchWithScore: 通道发送成功 channel={} totalRate={}",
     "dispatchWithScore: channel send success: channel={} totalRate={}",
     "message.log.server.channelrouter.info.dispatch.send.success.cn")
_reg("dispatchWithScore: 通道发送失败尝试下一个 channel={} err={}",
     "dispatchWithScore: channel send failed, trying next: channel={} err={}",
     "message.log.server.channelrouter.warn.dispatch.send.failed.try.next.cn")
_reg("dispatchWithScore: 所有通道均失败， lastError={}",
     "dispatchWithScore: all channels failed, lastError={}",
     "message.log.server.channelrouter.error.dispatch.all.failed.cn")

# Common: send success
for tag in ("DINGTALK", "FEISHU", "WEBHOOK", "DINGTALK_WORK", "AlipayMiniChannel",
            "[AlipayMiniChannel][MOCK]", "[GetuiPush]", "[SmsChannel]", "[PushChannel]"):
    _reg(f"[{tag}] 发送成功",
         f"[{tag}] Send success",
         f"message.log.server.channel.{tag.lower().replace('[', '').replace(']', '').replace('_', '.')}.info.send.success")
    _reg(f"[{tag}] 发送成功: receiver={{}} template={{}}",
         f"[{tag}] Send success: receiver={{}} template={{}}",
         f"message.log.server.channel.{tag.lower().replace('[', '').replace(']', '').replace('_', '.')}.info.send.success.receiver")

_reg("发送成功",
     "Send success",
     "message.log.server.channel.info.send.success")

# Send fail
for tag in ("DINGTALK", "FEISHU", "WEBHOOK", "DINGTALK_WORK"):
    _reg(f"[{tag}] 发送失败: errcode={{}} errmsg={{}}",
         f"[{tag}] Send failed: errcode={{}} errmsg={{}}",
         f"message.log.server.channel.{tag.lower()}.error.send.failed.errcode")
    _reg(f"[{tag}] 发送失败: status={{}}",
         f"[{tag}] Send failed: status={{}}",
         f"message.log.server.channel.{tag.lower()}.error.send.failed.status")

# Send exception
for tag in ("DINGTALK", "FEISHU", "WEBHOOK", "DINGTALK_WORK"):
    _reg(f"[{tag}] 发送异常: reason={{}}",
         f"[{tag}] Send exception: reason={{}}",
         f"message.log.server.channel.{tag.lower()}.error.send.exception")
# AliyunSms
_reg("[AliyunSms] 凭证未配置,发送失败: phone={}",
     "[AliyunSms] Credentials not configured, send failed: phone={}",
     "message.log.server.aliyunsms.error.credential.not.configured")
# Webhook
_reg("[WEBHOOK] 未配置 Webhook URL，跳过发送: receiver={}",
     "[WEBHOOK] Webhook URL not configured, skipping send: receiver={}",
     "message.log.server.webhook.warn.not.configured")
_reg("[WEBHOOK] 已添加签名: timestamp={}",
     "[WEBHOOK] Signature added: timestamp={}",
     "message.log.server.webhook.debug.signature.added")
_reg("[WEBHOOK] 发送成功: url={} status={}",
     "[WEBHOOK] Send success: url={} status={}",
     "message.log.server.webhook.info.send.success")
_reg("[WEBHOOK] 发送失败: url={} status={} body={}",
     "[WEBHOOK] Send failed: url={} status={} body={}",
     "message.log.server.webhook.error.send.failed")
_reg("[WEBHOOK] 未配置 Webhook URL, 跳过发送: receiver={}",
     "[WEBHOOK] Webhook URL not configured, skipping send: receiver={}",
     "message.log.server.webhook.warn.not.configured.raw")
# DingTalk + DingTalkWorkNotification + Feishu
_reg("未配置 access_token，跳过发送: receiver={}",
     "access_token not configured, skipping send: receiver={}",
     "message.log.server.channel.warn.access_token.not.configured")
_reg("未配置 access_token, 跳过发送: receiver={}",
     "access_token not configured, skipping send: receiver={}",
     "message.log.server.channel.warn.access_token.not.configured.raw")
_reg("未配置 hook，跳过发送: receiver={}",
     "hook not configured, skipping send: receiver={}",
     "message.log.server.feishu.warn.hook.not.configured")
_reg("刷新 access_token 成功",
     "Refresh access_token success",
     "message.log.server.channel.info.token.refresh.success")
_reg("获取 access_token 失败: errcode={} errmsg={}",
     "Get access_token failed: errcode={} errmsg={}",
     "message.log.server.channel.error.token.get.failed")
_reg("获取 access_token 异常: {}",
     "Get access_token exception: {}",
     "message.log.server.channel.error.token.exception")
_reg("加签失败,放弃发送: {}",
     "Signature failed, abandoning send: {}",
     "message.log.server.channel.error.signature.failed")
_reg("加签失败，放弃发送: {}",
     "Signature failed, abandoning send: {}",
     "message.log.server.channel.error.signature.failed.cn")
# AlipayMini
_reg("[AlipayMiniChannel] 未配置 AppID/privateKey,降级为日志输出: receiver={}",
     "[AlipayMiniChannel] AppID/privateKey not configured, degrading to log output: receiver={}",
     "message.log.server.alipaymini.warn.not.configured")
_reg("[AlipayMiniChannel] 发送成功: receiver={} template={}",
     "[AlipayMiniChannel] Send success: receiver={} template={}",
     "message.log.server.alipaymini.info.send.success")
_reg("[AlipayMiniChannel] 发送失败: receiver={} code={} msg={}",
     "[AlipayMiniChannel] Send failed: receiver={} code={} msg={}",
     "message.log.server.alipaymini.error.send.failed")
_reg("[AlipayMiniChannel] 发送异常: receiver={} err={}",
     "[AlipayMiniChannel] Send exception: receiver={} err={}",
     "message.log.server.alipaymini.error.send.exception")
_reg("[AlipayMiniChannel][MOCK] 模拟发送: receiver={} template={} content={}",
     "[AlipayMiniChannel][MOCK] Mock send: receiver={} template={} content={}",
     "message.log.server.alipaymini.mock.info.send")
# WechatWork + WeComApp
_reg("[WECOM] 未配置 key，跳过发送: receiver={}",
     "[WECOM] key not configured, skipping send: receiver={}",
     "message.log.server.wecom.warn.not.configured")
_reg("[WECOM_APP] 未启用或未配置 CorpID, 降级 mock: receiver={} content={}",
     "[WECOM_APP] CorpID not configured, degrading to mock: receiver={} content={}",
     "message.log.server.wecomapp.warn.not.configured.mock")
_reg("[WECOM] 发送成功: receiver={} template={}",
     "[WECOM] Send success: receiver={} template={}",
     "message.log.server.wecom.info.send.success")
_reg("[WECOM] 发送失败: receiver={} err={}",
     "[WECOM] Send failed: receiver={} err={}",
     "message.log.server.wecom.error.send.failed")
_reg("[WECOM] 发送异常: receiver={} err={}",
     "[WECOM] Send exception: receiver={} err={}",
     "message.log.server.wecom.error.send.exception")
_reg("[WECOM_APP] 发送成功: receiver={} template={}",
     "[WECOM_APP] Send success: receiver={} template={}",
     "message.log.server.wecomapp.info.send.success")
_reg("[WECOM_APP] 发送失败: receiver={} err={}",
     "[WECOM_APP] Send failed: receiver={} err={}",
     "message.log.server.wecomapp.error.send.failed")
_reg("[WECOM_APP] 发送异常: receiver={} err={}",
     "[WECOM_APP] Send exception: receiver={} err={}",
     "message.log.server.wecomapp.error.send.exception")
# WxMini
_reg("[WxMiniChannel] 未配置 AppID/AppSecret,降级为日志输出: receiver={}",
     "[WxMiniChannel] AppID/AppSecret not configured, degrading to log output: receiver={}",
     "message.log.server.wxmini.warn.not.configured")
_reg("[WxMiniChannel] 发送成功: receiver={} openid={}",
     "[WxMiniChannel] Send success: receiver={} openid={}",
     "message.log.server.wxmini.info.send.success")
_reg("[WxMiniChannel] 发送失败: receiver={} code={} msg={}",
     "[WxMiniChannel] Send failed: receiver={} code={} msg={}",
     "message.log.server.wxmini.error.send.failed")
_reg("[WxMiniChannel] 发送异常: receiver={} err={}",
     "[WxMiniChannel] Send exception: receiver={} err={}",
     "message.log.server.wxmini.error.send.exception")
# Email
_reg("发送成功：to={} subject={}",
     "Send success: to={} subject={}",
     "message.log.server.channel.email.info.send.success")
_reg("发送失败：to={}, reason={}",
     "Send failed: to={}, reason={}",
     "message.log.server.channel.email.warn.send.failed")
_reg("发送异常：to={} reason={}",
     "Send exception: to={} reason={}",
     "message.log.server.channel.email.error.send.exception")
_reg("[EMAIL] 发送成功: to={} subject={}",
     "[EMAIL] Send success: to={} subject={}",
     "message.log.server.email.info.send.success")
_reg("[EMAIL] 发送失败: to={}, reason={}",
     "[EMAIL] Send failed: to={}, reason={}",
     "message.log.server.email.warn.send.failed")
_reg("[EMAIL] 发送异常: to={} reason={}",
     "[EMAIL] Send exception: to={} reason={}",
     "message.log.server.email.error.send.exception")
# InApp
_reg("站内信发送未落库: receiver={} bizType={}",
     "In-app message not persisted: receiver={} bizType={}",
     "message.log.server.inapp.warn.not.persisted.raw")
_reg("站内信发送成功: receiver={} bizType={} count={} traceId={}",
     "In-app message sent: receiver={} bizType={} count={} traceId={}",
     "message.log.server.inapp.info.send.success.raw")
_reg("站内信发送异常: receiver={} err={}",
     "In-app message send exception: receiver={} err={}",
     "message.log.server.inapp.error.send.exception.raw")
_reg("[INAPP] 站内信发送未落库: receiver={} bizType={}",
     "[INAPP] In-app message not persisted: receiver={} bizType={}",
     "message.log.server.inapp.warn.not.persisted")
_reg("[INAPP] 站内信发送成功: receiver={} bizType={} count={} traceId={}",
     "[INAPP] In-app message sent: receiver={} bizType={} count={} traceId={}",
     "message.log.server.inapp.info.send.success")
_reg("[INAPP] 站内信发送异常: receiver={} err={}",
     "[INAPP] In-app message send exception: receiver={} err={}",
     "message.log.server.inapp.error.send.exception")
# Sms + Push
_reg("批量推送: provider={} count={} success={}",
     "Batch push: provider={} count={} success={}",
     "message.log.server.channel.push.info.batch")
_reg("批量推送：provider={} count={} success={}",
     "Batch push: provider={} count={} success={}",
     "message.log.server.channel.push.info.batch.cn")
_reg("批量发送: provider={} count={} success={}",
     "Batch send: provider={} count={} success={}",
     "message.log.server.channel.sms.info.batch")
_reg("批量发送：provider={} count={} success={}",
     "Batch send: provider={} count={} success={}",
     "message.log.server.channel.sms.info.batch.cn")
_reg("模板查询失败,忽略: code={} err={}",
     "Template query failed, ignoring: code={} err={}",
     "message.log.server.channel.sms.debug.template.query.failed.ignoring")
_reg("模板查询失败，忽略：code={} err={}",
     "Template query failed, ignoring: code={} err={}",
     "message.log.server.channel.sms.debug.template.query.failed.ignoring.cn")
# NotifyAdapter
_reg("[NotifyAdapter] 通道发送异常: channel={} receiver={} err={}",
     "[NotifyAdapter] Channel send exception: channel={} receiver={} err={}",
     "message.log.server.notifyadapter.error.channel.send.exception")
_reg("[NotifyAdapter] 模板发送异常: channel={} template={} err={}",
     "[NotifyAdapter] Template send exception: channel={} template={} err={}",
     "message.log.server.notifyadapter.error.template.send.exception")
# GetuiPushProvider
_reg("[GetuiPush] 推送成功: cid={} taskId={}",
     "[GetuiPush] Push success: cid={} taskId={}",
     "message.log.server.getui.info.send.success")
_reg("[GetuiPush] 推送失败: cid={} code={} msg={}",
     "[GetuiPush] Push failed: cid={} code={} msg={}",
     "message.log.server.getui.error.send.failed")
_reg("[GetuiPush] 推送异常: reason={}",
     "[GetuiPush] Push exception: reason={}",
     "message.log.server.getui.error.send.exception")
_reg("[GetuiPushSigner] 签名失败: {}",
     "[GetuiPushSigner] Sign failed: {}",
     "message.log.server.getui.signer.error.failed")
# Recall
_reg("[RecallChannel] DINGTALK 撤回尝试: msgId={} traceId={}",
     "[RecallChannel] DINGTALK recall attempt: msgId={} traceId={}",
     "message.log.server.recallchannel.dingtalk.info.recall.attempt")
_reg("[RecallChannel] FEISHU 撤回尝试: msgId={} traceId={}",
     "[RecallChannel] FEISHU recall attempt: msgId={} traceId={}",
     "message.log.server.recallchannel.feishu.info.recall.attempt")
_reg("[RecallChannel] WECOM_APP 撤回尝试: msgId={} traceId={}",
     "[RecallChannel] WECOM_APP recall attempt: msgId={} traceId={}",
     "message.log.server.recallchannel.wecom.app.info.recall.attempt")
_reg("[RecallRouter] 通道撤回实现重复注册,已忽略: channel={} existing={} ignored={}",
     "[RecallRouter] Recall channel implementation duplicate registration, ignored: channel={} existing={} ignored={}",
     "message.log.server.recallrouter.warn.duplicate.registration")
_reg("[RecallChannel] DINGTALK 撤回成功: msgId={}",
     "[RecallChannel] DINGTALK recall success: msgId={}",
     "message.log.server.recallchannel.dingtalk.info.recall.success")
_reg("[RecallChannel] DINGTALK 撤回失败: msgId={} code={} msg={}",
     "[RecallChannel] DINGTALK recall failed: msgId={} code={} msg={}",
     "message.log.server.recallchannel.dingtalk.error.recall.failed")
_reg("[RecallChannel] DINGTALK 撤回异常: msgId={} err={}",
     "[RecallChannel] DINGTALK recall exception: msgId={} err={}",
     "message.log.server.recallchannel.dingtalk.error.recall.exception")
_reg("[RecallChannel] FEISHU 撤回成功: msgId={}",
     "[RecallChannel] FEISHU recall success: msgId={}",
     "message.log.server.recallchannel.feishu.info.recall.success")
_reg("[RecallChannel] FEISHU 撤回失败: msgId={} code={} msg={}",
     "[RecallChannel] FEISHU recall failed: msgId={} code={} msg={}",
     "message.log.server.recallchannel.feishu.error.recall.failed")
_reg("[RecallChannel] FEISHU 撤回异常: msgId={} err={}",
     "[RecallChannel] FEISHU recall exception: msgId={} err={}",
     "message.log.server.recallchannel.feishu.error.recall.exception")
_reg("[RecallChannel] WECOM_APP 撤回成功: msgId={}",
     "[RecallChannel] WECOM_APP recall success: msgId={}",
     "message.log.server.recallchannel.wecom.app.info.recall.success")
_reg("[RecallChannel] WECOM_APP 撤回失败: msgId={} code={} msg={}",
     "[RecallChannel] WECOM_APP recall failed: msgId={} code={} msg={}",
     "message.log.server.recallchannel.wecom.app.error.recall.failed")
_reg("[RecallChannel] WECOM_APP 撤回异常: msgId={} err={}",
     "[RecallChannel] WECOM_APP recall exception: msgId={} err={}",
     "message.log.server.recallchannel.wecom.app.error.recall.exception")
# CrossModuleEvent
_reg("[CrossModuleSubscriber] 接收定时任务执行失败事件: aggregateId={}, payload={}",
     "[CrossModuleSubscriber] Received job execution failed event: aggregateId={}, payload={}",
     "message.log.server.crosmodulesub.warn.job.execution.failed")
_reg("[CrossModuleSubscriber] 接收 Agent 审批请求事件: aggregateId={}",
     "[CrossModuleSubscriber] Received Agent approval request event: aggregateId={}",
     "message.log.server.crosmodulesub.info.agent.approval")
_reg("[CrossModuleSubscriber] 接收流程审批通过事件: aggregateId={}",
     "[CrossModuleSubscriber] Received flow instance approved event: aggregateId={}",
     "message.log.server.crosmodulesub.info.flow.approved")
_reg("[CrossModuleSubscriber] 审批通过事件无发起人，跳过通知: aggregateId={}",
     "[CrossModuleSubscriber] Approval event has no initiator, skipping notification: aggregateId={}",
     "message.log.server.crosmodulesub.debug.flow.approved.no.initiator.skipped")
_reg("[CrossModuleSubscriber] 审批通过事件无发起人,跳过通知: aggregateId={}",
     "[CrossModuleSubscriber] Approval event has no initiator, skipping notification: aggregateId={}",
     "message.log.server.crosmodulesub.debug.flow.approved.no.initiator.skipped.cn")
_reg("[CrossModuleSubscriber] 接收流程审批驳回事件: aggregateId={}",
     "[CrossModuleSubscriber] Received flow instance rejected event: aggregateId={}",
     "message.log.server.crosmodulesub.info.flow.rejected")
_reg("[CrossModuleSubscriber] 审批驳回事件无发起人，跳过通知: aggregateId={}",
     "[CrossModuleSubscriber] Rejection event has no initiator, skipping notification: aggregateId={}",
     "message.log.server.crosmodulesub.debug.flow.rejected.no.initiator.skipped")
_reg("[CrossModuleSubscriber] 接收流程终止事件: aggregateId={}",
     "[CrossModuleSubscriber] Received flow instance terminated event: aggregateId={}",
     "message.log.server.crosmodulesub.info.flow.terminated")
_reg("[CrossModuleSubscriber] 接收项目立项审批通过事件: aggregateId={}",
     "[CrossModuleSubscriber] Received project initiation approved event: aggregateId={}",
     "message.log.server.crosmodulesub.info.project.initiation.approved")
_reg("[CrossModuleSubscriber] 项目立项事件无项目经理，跳过通知: aggregateId={}",
     "[CrossModuleSubscriber] Project initiation event has no project manager, skipping notification: aggregateId={}",
     "message.log.server.crosmodulesub.debug.project.no.manager.skipped")
_reg("[CrossModuleSubscriber] 接收合同签订事件: aggregateId={}",
     "[CrossModuleSubscriber] Received contract signed event: aggregateId={}",
     "message.log.server.crosmodulesub.info.contract.signed")
_reg("[CrossModuleSubscriber] 接收定时任务超时事件: aggregateId={}",
     "[CrossModuleSubscriber] Received job timeout event: aggregateId={}",
     "message.log.server.crosmodulesub.warn.job.timeout")
# TenantConfigService
_reg("[TenantConfig] 缓存命中: tenant={}",
     "[TenantConfig] Cache hit: tenant={}",
     "message.log.server.tenantconfig.debug.cache.hit")
_reg("[TenantConfig] 缓存读取异常(fail-open): tenant={} err={}",
     "[TenantConfig] Cache read exception (fail-open): tenant={} err={}",
     "message.log.server.tenantconfig.warn.cache.read.exception")
_reg("[TenantConfig] 配置不存在: tenant={}",
     "[TenantConfig] Configuration not found: tenant={}",
     "message.log.server.tenantconfig.debug.config.not.found")
_reg("[TenantConfig] 缓存写入异常(忽略): tenant={} err={}",
     "[TenantConfig] Cache write exception (ignored): tenant={} err={}",
     "message.log.server.tenantconfig.warn.cache.write.exception")
_reg("[TenantConfig] 通道覆盖: tenant={} channel={} enabled={}",
     "[TenantConfig] Channel override: tenant={} channel={} enabled={}",
     "message.log.server.tenantconfig.debug.channel.override")
_reg("[TenantConfig] channelOverrides 解析异常(fail-open): tenant={} err={}",
     "[TenantConfig] channelOverrides parse exception (fail-open): tenant={} err={}",
     "message.log.server.tenantconfig.warn.channeloverride.parse.exception")
_reg("[TenantConfig] provider覆盖: tenant={} channel={} provider={}",
     "[TenantConfig] Provider override: tenant={} channel={} provider={}",
     "message.log.server.tenantconfig.debug.provider.override")
_reg("[TenantConfig] providerOverrides 解析异常(fail-open): tenant={} err={}",
     "[TenantConfig] providerOverrides parse exception (fail-open): tenant={} err={}",
     "message.log.server.tenantconfig.warn.provideroverride.parse.exception")
_reg("[TenantConfig] 每日限额: tenant={} dailyLimit={}",
     "[TenantConfig] Daily quota: tenant={} dailyLimit={}",
     "message.log.server.tenantconfig.debug.daily.limit")
_reg("[TenantConfig] 每小时限额: tenant={} hourlyLimit={}",
     "[TenantConfig] Hourly quota: tenant={} hourlyLimit={}",
     "message.log.server.tenantconfig.debug.hourly.limit")
# OfflineMessageService
_reg("[WS-Offline] 缓存离线消息: userId={}, type={}",
     "[WS-Offline] Caching offline message: userId={}, type={}",
     "message.log.server.woffline.debug.cache.message")
_reg("[WS-Offline] 缓存离线消息失败，降级忽略: userId={}, err={}",
     "[WS-Offline] Failed to cache offline message, degrading: userId={}, err={}",
     "message.log.server.woffline.warn.cache.message.failed")
_reg("[WS-Offline] 从数据库拉取离线消息: userId={}, count={}",
     "[WS-Offline] Pulled offline messages from DB: userId={}, count={}",
     "message.log.server.woffline.info.fetch.from.db")
_reg("[WS-Offline] 数据库离线消息拉取失败: userId={}, err={}",
     "[WS-Offline] Failed to pull offline messages from DB: userId={}, err={}",
     "message.log.server.woffline.warn.db.pull.failed")
_reg("[WS-Offline] 拉取离线消息: userId={}, total={}",
     "[WS-Offline] Pull offline messages: userId={}, total={}",
     "message.log.server.woffline.info.pull.result")
_reg("[WS-Offline] Redis 计数失败: {}",
     "[WS-Offline] Redis count failed: {}",
     "message.log.server.woffline.warn.redis.count.failed")
_reg("[WS-Offline] DB 计数失败: {}",
     "[WS-Offline] DB count failed: {}",
     "message.log.server.woffline.warn.db.count.failed")
_reg("[WS-Offline] 溢出消息持久化到数据库: userId={}, count={}",
     "[WS-Offline] Persisting overflow messages to DB: userId={}, count={}",
     "message.log.server.woffline.info.overflow.persist.to.db")
_reg("[WS-Offline] 溢出消息持久化失败: userId={}, err={}",
     "[WS-Offline] Failed to persist overflow messages: userId={}, err={}",
     "message.log.server.woffline.warn.overflow.persist.failed")
# BatchServiceImpl
_reg("[Batch] executeBatch 批次不存在: batchId={}",
     "[Batch] executeBatch batch not found: batchId={}",
     "message.log.server.batch.warn.not.found")
_reg("[Batch] executeBatch payload 为空: batchId={}",
     "[Batch] executeBatch payload empty: batchId={}",
     "message.log.server.batch.warn.payload.empty")
_reg("[Batch] payload 解析失败: {}",
     "[Batch] Payload parse failed: {}",
     "message.log.server.batch.error.payload.parse.failed")
_reg("[Batch] doExecuteBatch 开始: batchId={}, requests={}, incremental={}",
     "[Batch] doExecuteBatch start: batchId={}, requests={}, incremental={}",
     "message.log.server.batch.info.execute.start")
_reg("[Batch] 批次不存在，无法执行: batchId={}",
     "[Batch] Batch not found, cannot execute: batchId={}",
     "message.log.server.batch.warn.execute.not.executable")
_reg("[Batch] 单条发送失败: batchId={}, index={}, error={}",
     "[Batch] Single send failed: batchId={}, index={}, error={}",
     "message.log.server.batch.warn.single.send.failed")
_reg("[Batch] 单条业务异常: batchId={}, index={}, code={}, msg={}",
     "[Batch] Single message business exception: batchId={}, index={}, code={}, msg={}",
     "message.log.server.batch.warn.single.business.exception")
_reg("[Batch] 单条系统异常: batchId={}, index={}",
     "[Batch] Single message system exception: batchId={}, index={}",
     "message.log.server.batch.error.single.system.exception")
_reg("[Batch] doExecuteBatch 完成: batchId={}, status={}, success={}, failed={}, skipped={}",
     "[Batch] doExecuteBatch complete: batchId={}, status={}, success={}, failed={}, skipped={}",
     "message.log.server.batch.info.execute.complete")
_reg("[Batch] 进度持久化冲突，重试加载: batchId={}",
     "[Batch] Progress persistence conflict, retrying load: batchId={}",
     "message.log.server.batch.warn.progress.persistence.conflict")
_reg("[Batch] 进度持久化异常: batchId={}, err={}",
     "[Batch] Progress persistence exception: batchId={}, err={}",
     "message.log.server.batch.error.progress.persistence.exception")
_reg("[Batch] SSE 推送异常: batchId={}, err={}",
     "[Batch] SSE push exception: batchId={}, err={}",
     "message.log.server.batch.warn.sse.push.exception")

# SSE
_reg("SSE 用户连接已过期：receiver={}",
     "SSE user connection expired: receiver={}",
     "message.log.server.sse.warn.channel.expired")
_reg("SSE 用户连接已过期: receiver={}",
     "SSE user connection expired: receiver={}",
     "message.log.server.sse.warn.channel.expired.raw")
_reg("SSE 发送异常：receiver={} err={}",
     "SSE send exception: receiver={} err={}",
     "message.log.server.sse.warn.send.exception.cn")
_reg("SSE 发送异常: receiver={} err={}",
     "SSE send exception: receiver={} err={}",
     "message.log.server.sse.warn.send.exception")
_reg("SSE 订阅：receiver={}",
     "SSE subscribe: receiver={}",
     "message.log.server.sse.info.subscribe")
_reg("SSE 订阅: receiver={}",
     "SSE subscribe: receiver={}",
     "message.log.server.sse.info.subscribe.raw")
_reg("SSE 发送成功：receiver={}",
     "SSE send success: receiver={}",
     "message.log.server.sse.info.send.success.cn")
_reg("SSE 发送成功: receiver={}",
     "SSE send success: receiver={}",
     "message.log.server.sse.info.send.success.raw")
_reg("SSE 发送失败：localEvent 为 null：receiver={}",
     "SSE send failed (localEvent is null): receiver={}",
     "message.log.server.sse.warn.send.to.local.event.cn")
_reg("SSE 标记所有消息已读：receiver={}",
     "SSE marked all messages as read: receiver={}",
     "message.log.server.sse.info.read.all.complete")
_reg("SSE 全部已读：receiver={}",
     "SSE all read: receiver={}",
     "message.log.server.sse.info.complete.all.read")
_reg("SSE 发送失败并兜底出站异常：receiver={} err1={} err2={}",
     "SSE send failed and outbox out fallback exception: receiver={} err1={} err2={}",
     "message.log.server.sse.warn.send.error.with.outbox.out.exception")
_reg("SSE 发送失败并兜底入站异常：receiver={} err1={} err2={}",
     "SSE send failed and outbox in fallback exception: receiver={} err1={} err2={}",
     "message.log.server.sse.warn.send.error.with.outbox.in.exception")
# Outbox
_reg("领域事件反序列化 payload 为空：aggregateId={} event={}",
     "Domain event payload is empty after deserialization: aggregateId={} event={}",
     "message.log.server.outbox.warn.parse.null")
_reg("领域事件反序列化失败：err={}",
     "Domain event deserialization failed: err={}",
     "message.log.server.outbox.warn.parse.failed")
_reg("领域事件发布到 RocketMQ 异常：err={}",
     "Publishing domain event to RocketMQ exception: err={}",
     "message.log.server.outbox.error.publish.exception")
# Fallbacks
_reg("[MessageSendClient] 降级触发: {}",
     "[MessageSendClient] Fallback triggered: {}",
     "message.log.server.messagesendclient.warn.triggered.raw")
_reg("[MessageSendClient] 降级触发：{}",
     "[MessageSendClient] Fallback triggered: {}",
     "message.log.server.messagesendclient.warn.triggered")
_reg("[NotificationClient] 降级触发: {}",
     "[NotificationClient] Fallback triggered: {}",
     "message.log.server.notificationclient.warn.triggered.raw")
_reg("[NotificationClient] 广播降级: topic={}, reason={}",
     "[NotificationClient] Broadcast fallback: topic={}, reason={}",
     "message.log.server.notificationclient.warn.broadcast")
_reg("[NotificationClient] 实时降级: userId={}, reason={}",
     "[NotificationClient] Realtime fallback: userId={}, reason={}",
     "message.log.server.notificationclient.warn.pushrealtime")
# Rest of service layer message keys (identified via dry-run below, we expose them)
_reg("站内信发送未落库：receiver={} bizType={}",
     "In-app message not persisted: receiver={} bizType={}",
     "message.log.server.inapp.warn.not.persisted.cn")
_reg("站内信发送成功：receiver={} bizType={} count={} traceId={}",
     "In-app message sent: receiver={} bizType={} count={} traceId={}",
     "message.log.server.inapp.info.send.success.cn")
_reg("站内信发送异常：receiver={} err={}",
     "In-app message send exception: receiver={} err={}",
     "message.log.server.inapp.error.send.exception.cn")
_reg("发送失败：url={} status={} body={}",
     "Send failed: url={} status={} body={}",
     "message.log.server.channel.http.send.failed.cn")
_reg("发送成功：url={} status={}",
     "Send success: url={} status={}",
     "message.log.server.channel.http.send.success.cn")
# Add placeholder form
_reg("未启用或未配置 AppKey, 降级 mock: receiver={} content={}",
     "Not enabled or AppKey not configured, degrading to mock: receiver={} content={}",
     "message.log.server.channel.warn.not.enabled.mock.raw")
_reg("未配置 AppID/privateKey,降级为日志输出: receiver={}",
     "AppID/privateKey not configured, degrading to log output: receiver={}",
     "message.log.server.alipaymini.warn.not.configured.raw")

# Fallbacks for any unmatched literal (auto-translate): use canonical_key + fallback entry.


def pick_key(literal: str) -> tuple[str, str, str]:
    """Return (key, en, zh) — looking up by zh match; if missing, synthesise."""
    if literal in L2K:
        k = L2K[literal]
        return k, T[k]["en"], T[k]["zh"]
    if literal in T:
        return literal, T[literal]["en"], T[literal]["zh"]
    # Synthesise
    cleaned = re.sub(r"[^A-Za-z0-9_{} ]", " ", literal).strip()
    cleaned = re.sub(r"\s+", "_", cleaned).strip("_")[:100]
    sig = hashlib.md5(literal.encode("utf-8")).hexdigest()[:6]
    k = f"message.log.other.{cleaned}.{sig}"
    T.setdefault(k, {"zh": literal, "en": cleaned, "tw": literal, "default": cleaned})
    return k, T[k]["en"], T[k]["zh"]


# ----------------------------------------------------------------------------
# Scan — brace-aware
# ----------------------------------------------------------------------------
def discover() -> list[tuple[Path, int, int, str, str]]:
    """Return list of (path, line_no, col_offset, literal, rest).

    Uses brace/paren tracking to capture multi-line log calls.
    """
    out: list[tuple[Path, int, int, str, str]] = []
    for jp in sorted(ROOT.rglob("*.java")):
        rel = str(jp).replace("\\", "/")
        if any(f in rel for f in SKIP_FILES):
            continue
        if "/infra/" in rel:
            continue
        try:
            src = jp.read_text(encoding="utf-8")
        except Exception as e:
            print("skip", rel, e, file=sys.stderr)
            continue
        _scan_one(jp, src, out)
    return out


def _scan_one(jp: Path, src: str, out: list) -> None:
    i = 0
    n = len(src)
    while i < n:
        # Try to find 'log.' followed by severity keyword and '('
        m = re.search(r'log\.(info|warn|error|debug)\(', src[i:])
        if not m:
            return
        sev = m.group(1)
        start = i + m.start()
        depth = 1
        j = i + m.end()
        while j < n and depth > 0:
            ch = src[j]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        if depth != 0:
            # Unterminated — bail
            return
        # Extract argument string (everything between the opening '(' and closing ')')
        body = src[i + m.end():j]
        # Drop leading whitespace/comma; check that the first non-space char is a quote
        stripped = body.lstrip(" \t")
        if not stripped.startswith('"'):
            i = j + 1
            continue
        # Parse the first string literal
        k = 1
        lit_chars: list[str] = []
        while k < len(stripped):
            ch = stripped[k]
            if ch == "\\":
                nxt = stripped[k + 1] if k + 1 < len(stripped) else ""
                lit_chars.append(ch)
                lit_chars.append(nxt)
                k += 2
                continue
            if ch == '"':
                break
            lit_chars.append(ch)
            k += 1
        literal = "".join(lit_chars)
        if not CJK.search(literal):
            i = j + 1
            continue
        rest = stripped[k:]  # includes closing quote and later args
        ln = src.count("\n", 0, start) + 1
        out.append((jp, ln, start, literal, rest, sev))
        i = j + 1


def rewrite_file(path: Path, hits: list[tuple[Path, int, int, str, str]],
                 cache: dict[str, tuple[str, str, str]]) -> None:
    """Mutate file in place."""
    src = path.read_text(encoding="utf-8")
    # Process hits from last to first (preserve offsets)
    for _, ln_off, byte_off, literal, rest, sev in sorted(hits, key=lambda x: -x[2]):
        # Reconstruct the original call text to replace
        i = byte_off
        # Find call start (already the offset)
        # Now find closing ')' via paren tracking
        n = len(src)
        # We need to identify where 'log.( severity (' starts and consume until matched ')'
        m = re.match(r'log\.(info|warn|error|debug)\(', src[i:])
        if not m:
            continue
        depth = 1
        j = i + m.end()
        while j < n and depth > 0:
            ch = src[j]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        if depth != 0:
            continue
        # The call is src[i:j+1]
        call = src[i:j + 1]
        # Replace first literal (which has CJK)
        # matched by the quoted literal
        new_call = call.replace(f'"{literal}"',
                                  f'I18n.message("{cache[literal][0]}")',
                                  1)
        src = src[:i] + new_call + src[j + 1:]
    # Append import if missing
    if IMPORT_LINE not in src:
        # Prepend after package line if present
        pm = re.search(r'^package\s+[a-zA-Z0-9_.]+\s*;\s*', src, re.MULTILINE)
        if pm:
            end = pm.end()
            src = src[:end] + "\n" + IMPORT_LINE + src[end:]
        else:
            src = IMPORT_LINE + "\n" + src
    path.write_text(src, encoding="utf-8")


def _merge_props(path: Path, entries: dict[str, str]) -> None:
    existing: dict[str, str] = {}
    if path.exists():
        for raw in path.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#") or "=" not in raw:
                continue
            k, _, v = raw.partition("=")
            existing[k.strip()] = v.strip()
    for k, v in entries.items():
        existing.setdefault(k, v)
    header: list[str] = []
    if path.exists():
        for raw in path.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#"):
                header.append(raw)
            else:
                break
    out = list(header) + [""] + [f"{k}={v}" for k, v in existing.items()] + [""]
    path.write_text("\n".join(out), encoding="utf-8")


def main():
    live = os.environ.get("REFACTOR_LIVE") == "1"
    print(f"[{'LIVE' if live else 'DRY-RUN'}] ydzs-message log hard-coded Chinese")

    hits = discover()
    print(f"Found {len(hits)} log calls with CJK across {len({str(h[0]) for h in hits})} files.")

    # Assign (key, en, zh) per literal (first occurrence stable)
    cache: dict[str, tuple[str, str, str]] = {}
    for _, _, _, lit, _, _ in hits:
        if lit not in cache:
            cache[lit] = pick_key(lit)
    matched_known = sum(1 for k in cache.values() if not k[0].startswith("message.log.other."))
    synth = sum(1 for k in cache.values() if k[0].startswith("message.log.other."))
    print(f"Matched: {matched_known} known, {synth} synthesised.")

    # Group by path
    grouped: dict[Path, list[tuple[Path, int, int, str, str, str]]] = defaultdict(list)
    for h in hits:
        grouped[h[0]].append(h)

    print("\nPer-file hit counts:")
    for p, hs in sorted(grouped.items()):
        print(f"  {len(hs):>3} -> {p}")

    print("\nSynthesised literals (first 40):")
    n = 0
    for lit, (k, en, zh) in cache.items():
        if k.startswith("message.log.other.") and n < 40:
            print(f"  zh: {lit}")
            print(f"  en (synthetic): {en}")
            n += 1

    if live:
        BACKUP_BASE.mkdir(parents=True, exist_ok=True)
        ts = _dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        backup = BACKUP_BASE / f"ydsz-message-{ts}"
        shutil.copytree(str(ROOT), str(backup),
                        ignore=shutil.ignore_patterns("target", ".git", "node_modules", "*.class"))
        print(f"\nBackup at {backup}")

        for p, hs in grouped.items():
            rewrite_file(p, hs, cache)

        _merge_props(PROPS["default"], {k: v["default"] for k, v in T.items()})
        _merge_props(PROPS["en"], {k: v["en"] for k, v in T.items()})
        _merge_props(PROPS["zh"], {k: v["zh"] for k, v in T.items()})
        _merge_props(PROPS["tw"], {k: v["tw"] for k, v in T.items()})
        print(f"T now has {len(T)} entries.")
        print("\nDone.")


if __name__ == "__main__":
    main()
