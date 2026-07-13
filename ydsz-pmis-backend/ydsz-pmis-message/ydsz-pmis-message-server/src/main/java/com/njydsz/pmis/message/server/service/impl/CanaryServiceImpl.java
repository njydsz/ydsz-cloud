package com.njydsz.pmis.message.server.service.impl.canary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.core.constant.PageConstants;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.dto.canary.CanaryUpsertDTO;
import com.njydsz.pmis.message.domain.entity.canary.MsgCanaryDO;
import com.njydsz.pmis.message.infra.mapper.canary.MsgCanaryMapper;
import com.njydsz.pmis.message.server.service.canary.CanaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson2.JSON;

/**
 * 灰度桶服务实现。
 *
 * <p>按 canaryKey upsert；命中判定按 {@code Math.floorMod(canaryKey.hashCode() ^ bucketValue.hashCode(), 100) < percentage}。
 * upsert 时重算 bucketSelected（前 percentage 个桶号）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanaryServiceImpl implements CanaryService {

    /** 默认灰度桶总数 */
    private static final int DEFAULT_BUCKET_TOTAL = 100;

    /** 灰度配置 Mapper */
    private final MsgCanaryMapper msgCanaryMapper;

    @Override
    public MsgCanaryDO upsert(CanaryUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCanaryKey())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "灰度键不能为空");
        }
        int total = dto.getBucketTotal() == null || dto.getBucketTotal() <= 0 ? DEFAULT_BUCKET_TOTAL : dto.getBucketTotal();
        int percentage = dto.getPercentage() == null ? 0 : Math.max(0, Math.min(100, dto.getPercentage()));
        MsgCanaryDO existing = msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanaryDO>()
                .eq(MsgCanaryDO::getCanaryKey, dto.getCanaryKey())
                .last("LIMIT 1"));
        String bucketSelected = buildBucketSelected(total, percentage);
        if (existing == null) {
            MsgCanaryDO entity = new MsgCanaryDO();
            entity.setCanaryKey(dto.getCanaryKey());
            entity.setBucketTotal(total);
            entity.setBucketSelected(bucketSelected);
            entity.setPercentage(percentage);
            entity.setExperimentTemplateCode(dto.getExperimentTemplateCode());
            entity.setExperimentChannel(dto.getExperimentChannel());
            entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
            entity.setDescription(dto.getDescription());
            entity.setTenantId(TenantContext.getTenantId());
            msgCanaryMapper.insert(entity);
            log.info("[Canary] 新建灰度桶: key={} percentage={} expTpl={} expChan={}",
                    dto.getCanaryKey(), percentage, dto.getExperimentTemplateCode(), dto.getExperimentChannel());
            return entity;
        }
        existing.setBucketTotal(total);
        existing.setBucketSelected(bucketSelected);
        existing.setPercentage(percentage);
        existing.setExperimentTemplateCode(dto.getExperimentTemplateCode());
        existing.setExperimentChannel(dto.getExperimentChannel());
        if (StringUtils.hasText(dto.getStatus())) {
            existing.setStatus(dto.getStatus());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        msgCanaryMapper.updateById(existing);
        return existing;
    }

    @Override
    public boolean hit(String canaryKey, String bucketValue) {
        return matchConfig(canaryKey, bucketValue) != null;
    }

    @Override
    public MsgCanaryDO matchConfig(String canaryKey, String bucketValue) {
        if (!StringUtils.hasText(canaryKey) || !StringUtils.hasText(bucketValue)) {
            return null;
        }
        MsgCanaryDO config = msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanaryDO>()
                .eq(MsgCanaryDO::getCanaryKey, canaryKey)
                .eq(MsgCanaryDO::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (config == null || config.getPercentage() == null || config.getPercentage() <= 0) {
            return null;
        }
        int bucket = Math.floorMod(canaryKey.hashCode() ^ bucketValue.hashCode(), 100);
        return bucket < config.getPercentage() ? config : null;
    }

    @Override
    public MsgCanaryDO getByKey(String canaryKey) {
        if (!StringUtils.hasText(canaryKey)) {
            return null;
        }
        return msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanaryDO>()
                .eq(MsgCanaryDO::getCanaryKey, canaryKey)
                .last("LIMIT 1"));
    }

    @Override
    public Page<MsgCanaryDO> page(PageQuery query) {
        Page<MsgCanaryDO> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        return msgCanaryMapper.selectPage(page, new LambdaQueryWrapper<MsgCanaryDO>()
                .orderByDesc(MsgCanaryDO::getCreatedAt));
    }

    /**
     * 构造命中桶列表 JSON（前 percentage 个桶号）。
     *
     * @param total      桶总数
     * @param percentage 灰度比例
     * @return 形如 [0,1,2] 的 JSON 字符串
     */
    private String buildBucketSelected(int total, int percentage) {
        int count = Math.min(percentage, total);
        List<Integer> buckets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buckets.add(i);
        }
        return JSON.toJSONString(buckets);
    }
}
