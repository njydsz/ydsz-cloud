package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.CanaryUpsertDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.mapper.MsgCanaryMapper;
import com.njydsz.pmis.message.service.CanaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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

    private static final int DEFAULT_BUCKET_TOTAL = 100;

    private final MsgCanaryMapper msgCanaryMapper;

    @Override
    public MsgCanaryDO upsert(CanaryUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCanaryKey())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "灰度键不能为空");
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
            entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
            entity.setDescription(dto.getDescription());
            entity.setTenantId(TenantContext.getTenantId());
            msgCanaryMapper.insert(entity);
            log.info("[Canary] 新建灰度桶: key={} percentage={}", dto.getCanaryKey(), percentage);
            return entity;
        }
        existing.setBucketTotal(total);
        existing.setBucketSelected(bucketSelected);
        existing.setPercentage(percentage);
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
        if (!StringUtils.hasText(canaryKey) || !StringUtils.hasText(bucketValue)) {
            return false;
        }
        MsgCanaryDO config = msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanaryDO>()
                .eq(MsgCanaryDO::getCanaryKey, canaryKey)
                .eq(MsgCanaryDO::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (config == null || config.getPercentage() == null || config.getPercentage() <= 0) {
            return false;
        }
        int bucket = Math.floorMod(canaryKey.hashCode() ^ bucketValue.hashCode(), 100);
        return bucket < config.getPercentage();
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
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
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
        return com.alibaba.fastjson2.JSON.toJSONString(buckets);
    }
}
