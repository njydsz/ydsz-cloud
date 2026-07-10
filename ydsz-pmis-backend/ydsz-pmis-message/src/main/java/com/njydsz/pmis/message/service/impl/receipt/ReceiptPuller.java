package com.njydsz.pmis.message.service.impl.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.dto.receipt.ReceiptResult;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import com.njydsz.pmis.message.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.enums.receipt.ReceiptStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.service.core.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * P2-9: 回执闭环调度器 —— 主动拉取回执 + 超时补偿。
 *
 * <p>对标阿里云 MessageCenter / 腾讯云 CAM 的回执闭环能力。仅依赖服务商被动回调会导致
 * 大量消息长期停留在「回执未知」状态（{@code receiptStatus=NONE}），本调度器通过两个阶段
 * 补齐闭环：
 *
 * <ol>
 *   <li><b>主动拉取阶段</b>：扫描 {@code status=SUCCESS AND receiptStatus=NONE
 *       AND createdAt < now - pullDelayMinutes} 的消息，调用对应渠道
 *       {@link MessageChannel#queryReceipt} 向服务商查询最新回执状态。
 *       <ul>
 *         <li>渠道支持且返回结果 → {@link MessageLogService#updateReceipt} 更新回执状态</li>
 *         <li>渠道不支持（{@link Optional#empty()}）→ 跳过，仅等待被动回调</li>
 *         <li>拉取异常 → 记录 WARN，不中断后续消息处理</li>
 *       </ul>
 *   </li>
 *   <li><b>超时补偿阶段</b>：对于 {@code createdAt < now - timeoutMinutes} 仍无回执的消息，
 *       标记 {@code receiptStatus=TIMEOUT}，避免消息永远停留在「回执未知」状态。
 *       超时判定优先于拉取（说明此前已尝试拉取但仍无结果）。</li>
 * </ol>
 *
 * <p>多实例部署通过 Redisson 分布式锁保证只有一个实例执行扫描，锁等待 0s（不阻塞），
 * TTL 60s，获取失败直接跳过本次扫描。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "pmis.message", name = "receipt-pull-enabled", havingValue = "true", matchIfMissing = true)
public class ReceiptPuller {

    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;
    private final MessageLogService messageLogService;
    private final MessageProperties messageProperties;
    private final RedissonClient redissonClient;

    /**
     * 定时扫描回执缺失的消息。
     *
     * <p>默认 120s 扫描一次，通过 {@code pmis.message.receipt-pull-scan-interval-ms} 配置。
     * 分布式锁 TTL 60s，等待 0s（不阻塞），获取失败直接跳过。
     */
    @Scheduled(fixedDelayString = "${pmis.message.receipt-pull-scan-interval-ms:120000}")
    public void scan() {
        RLock lock = redissonClient.getLock(MessageConstants.RECEIPT_PULL_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("[ReceiptPuller] 未获取锁,跳过本次扫描");
                return;
            }
            doScan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ReceiptPuller] 扫描被中断");
        } catch (Exception e) {
            log.error("[ReceiptPuller] 扫描异常: {}", e.getMessage(), e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行回执拉取与超时补偿扫描。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        // 拉取阈值：发送成功后 pullDelayMinutes 分钟才开始主动拉取（给服务商回调留窗口）
        LocalDateTime pullThreshold = now.minusMinutes(messageProperties.getReceiptPullDelayMinutes());
        // 超时阈值：超过 timeoutMinutes 仍无回执则标记 TIMEOUT
        LocalDateTime timeoutThreshold = now.minusMinutes(messageProperties.getReceiptTimeoutMinutes());

        List<MsgLogDO> pending = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, MessageStatusEnum.SUCCESS.name())
                .eq(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.NONE.name())
                .lt(MsgLogDO::getCreatedAt, pullThreshold)
                .last("LIMIT " + MessageConstants.RECEIPT_PULL_BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        log.info("[ReceiptPuller] 待处理回执缺失消息 {} 条", pending.size());

        int pulled = 0;
        int updated = 0;
        int timeout = 0;
        int skipped = 0;
        for (MsgLogDO logDO : pending) {
            try {
                // ① 超时优先：超过超时阈值仍无回执 → 标记 TIMEOUT
                if (logDO.getCreatedAt() != null && logDO.getCreatedAt().isBefore(timeoutThreshold)) {
                    messageLogService.updateReceipt(logDO.getId(),
                            ReceiptStatusEnum.TIMEOUT.name(), LocalDateTime.now());
                    timeout++;
                    continue;
                }
                pulled++;
                // ② 主动拉取：调用渠道 queryReceipt
                MessageChannel channel = channelRouter.route(logDO.getChannel());
                Optional<ReceiptResult> result = channel.queryReceipt(logDO);
                if (result.isEmpty()) {
                    // 渠道不支持主动拉取，跳过等待被动回调
                    skipped++;
                    continue;
                }
                ReceiptResult receipt = result.get();
                messageLogService.updateReceipt(logDO.getId(),
                        receipt.getStatus().name(), LocalDateTime.now());
                updated++;
            } catch (Exception e) {
                log.warn("[ReceiptPuller] 拉取回执异常: logId={} err={}",
                        logDO.getId(), e.getMessage());
                skipped++;
            }
        }
        log.info("[ReceiptPuller] 扫描完成: total={} pulled={} updated={} timeout={} skipped={}",
                pending.size(), pulled, updated, timeout, skipped);
    }
}
