package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.workflow.flow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowCcDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowCcMapper;
import com.njydsz.pmis.workflow.flow.service.FlowCcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCcServiceImpl implements FlowCcService {

    private final FlowCcMapper ccMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCc(FlowCcDO cc) {
        if (cc == null) {
            return null;
        }
        if (cc.getReadStatus() == null) {
            cc.setReadStatus("UNREAD");
        }
        if (cc.getProviderTraceId() == null) {
            cc.setProviderTraceId(TraceIdUtil.getOrCreate());
        }
        if (cc.getCreatedAt() == null) {
            cc.setCreatedAt(LocalDateTime.now());
        }
        if (cc.getUpdatedAt() == null) {
            cc.setUpdatedAt(LocalDateTime.now());
        }
        if (cc.getCcType() == null) {
            cc.setCcType("CC_NODE");
        }
        ccMapper.insert(cc);
        log.info("[FlowCc] 抄送写入: id={} instanceId={} ccUserId={} type={}",
                cc.getId(), cc.getInstanceId(), cc.getCcUserId(), cc.getCcType());
        return cc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveCcBatch(List<FlowCcDO> ccs) {
        if (ccs == null || ccs.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        String traceId = TraceIdUtil.getOrCreate();
        for (FlowCcDO cc : ccs) {
            if (cc.getReadStatus() == null) {
                cc.setReadStatus("UNREAD");
            }
            if (cc.getProviderTraceId() == null) {
                cc.setProviderTraceId(traceId);
            }
            if (cc.getCreatedAt() == null) {
                cc.setCreatedAt(now);
            }
            if (cc.getUpdatedAt() == null) {
                cc.setUpdatedAt(now);
            }
            if (cc.getCcType() == null) {
                cc.setCcType("CC_NODE");
            }
            ccMapper.insert(cc);
        }
        log.info("[FlowCc] 批量抄送写入: count={}", ccs.size());
        return ccs.size();
    }

    @Override
    public List<FlowCcDO> pageMyCc(Long tenantId, Long userId, FlowCcQueryDTO query) {
        if (query == null) {
            query = new FlowCcQueryDTO();
        }
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        int offset = (pageNum - 1) * pageSize;
        return ccMapper.selectCcByUserPage(tenantId, userId,
                query.getReadStatus(), query.getFlowCode(), offset, pageSize);
    }

    @Override
    public long countMyCc(Long tenantId, Long userId, FlowCcQueryDTO query) {
        if (query == null) {
            return 0L;
        }
        return ccMapper.countCcByUser(tenantId, userId, query.getReadStatus(), query.getFlowCode());
    }

    @Override
    public long countUnread(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return 0L;
        }
        return ccMapper.countCcUnreadByUser(tenantId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long tenantId, Long userId, Long id) {
        if (id == null || userId == null) {
            return false;
        }
        int n = ccMapper.markRead(id, userId, LocalDateTime.now());
        return n > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return 0;
        }
        return ccMapper.markAllRead(tenantId, userId, LocalDateTime.now());
    }
}
