package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.engine.AlertCodeGen;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;
import com.njydsz.pmis.execution.mapper.AlertDispatchMapper;
import com.njydsz.pmis.execution.service.AlertDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预警分级推送 Service 实现
 *
 * <p>按黄/红等级自动映射目标角色（PM/PMO/GM/CFO）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDispatchServiceImpl implements AlertDispatchService {

    private final AlertDispatchMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(AlertDispatchDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(dto.getAlertType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警类型不能为空");
        }
        String level = StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel().toUpperCase() : "YELLOW";
        if (!isValidLevel(level)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警等级不合法: " + level);
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预警标题不能为空");
        }

        AlertDispatchDO d = new AlertDispatchDO();
        BeanUtils.copyProperties(dto, d);
        d.setAlertLevel(level);
        if (!StringUtils.hasText(d.getAlertCode())) {
            d.setAlertCode(AlertCodeGen.next(dto.getAlertType(), level));
        }
        if (!StringUtils.hasText(d.getPushChannels())) {
            d.setPushChannels(level.equals("RED") ? "IN_APP,EMAIL" : "IN_APP");
        }
        if (!StringUtils.hasText(d.getTargetRole())) {
            d.setTargetRole(String.join(",", resolveTargetRoles(level)));
        }
        if (d.getRetryCount() == null) d.setRetryCount(0);
        if (d.getStatus() == null) d.setStatus("PENDING");
        if (d.getTenantId() == null) d.setTenantId(1L);
        if (d.getDispatchedAt() == null) d.setDispatchedAt(LocalDateTime.now());
        if (d.getProviderTraceId() == null) d.setProviderTraceId("");

        // 幂等：相同 alertCode 已存在则更新
        AlertDispatchDO exist = findByCode(d.getAlertCode());
        if (exist != null) {
            d.setId(exist.getId());
            mapper.updateById(d);
            log.info("[Alert] 幂等更新: code={} level={}", d.getAlertCode(), level);
            return exist.getId();
        }
        mapper.insert(d);
        log.info("[Alert] 提交预警: code={} type={} level={} roles={}",
                d.getAlertCode(), d.getAlertType(), level, d.getTargetRole());
        return d.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchNow(Long id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "预警不存在");
        }
        if ("SENT".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            return true;
        }
        try {
            // 简化：与通知中心解耦，本地标记 SENT
            // 真实环境下会调用 ydsz-pmis-notification OpenFeign
            int n = mapper.markSent(id, LocalDateTime.now());
            log.info("[Alert] 分发成功: id={} code={} level={} channels={}",
                    id, d.getAlertCode(), d.getAlertLevel(), d.getPushChannels());
            return n > 0;
        } catch (Exception e) {
            mapper.markFailed(id, e.getMessage());
            log.warn("[Alert] 分发失败: id={} err={}", id, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryFailed(int maxRetry) {
        if (maxRetry <= 0) maxRetry = 3;
        List<AlertDispatchDO> list = mapper.selectRetryable(LocalDateTime.now().minusMinutes(5), maxRetry);
        int n = 0;
        for (AlertDispatchDO d : list) {
            try {
                mapper.incrementRetry(d.getId());
                if (dispatchNow(d.getId())) {
                    n++;
                }
            } catch (Exception e) {
                log.warn("[Alert] 重试失败: id={} err={}", d.getId(), e.getMessage());
            }
        }
        return n;
    }

    @Override
    public List<String> resolveTargetRoles(String level) {
        if (level == null) return Collections.emptyList();
        return switch (level.toUpperCase()) {
            case "RED" -> List.of("PMO", "GM", "CFO");
            case "YELLOW" -> List.of("PM", "PMO");
            case "NORMAL" -> List.of("PM");
            default -> Collections.emptyList();
        };
    }

    @Override
    public List<AlertDispatchDO> listByLevelAndStatus(String level, String status) {
        return mapper.selectByLevelAndStatus(level, status);
    }

    @Override
    public List<Map<String, Object>> aggregateByTypeAndLevel(Long tenantId) {
        return mapper.aggregateByTypeAndLevel(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        }
        AlertDispatchDO d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "预警不存在");
        }
        if ("SENT".equals(d.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已发送的预警不可取消");
        }
        AlertDispatchDO update = new AlertDispatchDO();
        update.setId(id);
        update.setStatus("CANCELLED");
        update.setFailReason(reason);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
        log.info("[Alert] 取消预警: id={} reason={}", id, reason);
    }

    // ----------------- 私有 -----------------

    private boolean isValidLevel(String level) {
        return "YELLOW".equals(level) || "RED".equals(level) || "NORMAL".equals(level);
    }

    private AlertDispatchDO findByCode(String code) {
        if (!StringUtils.hasText(code)) return null;
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AlertDispatchDO>()
                        .eq("alert_code", code)
                        .eq("deleted", 0)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }
}
