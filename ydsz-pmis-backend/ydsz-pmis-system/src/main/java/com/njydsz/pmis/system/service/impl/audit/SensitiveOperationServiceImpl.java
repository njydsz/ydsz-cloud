package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.audit.SensitiveOperationDO;
import com.njydsz.pmis.system.mapper.audit.SensitiveOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 敏感操作审计服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveOperationServiceImpl {

    private final SensitiveOperationMapper mapper;

    /**
     * 分页查询敏感操作
     *
     * @param page   页码
     * @param size   每页大小
     * @param userId 用户 ID（可选）
     * @param opType 操作类型（可选）
     * @return 分页结果
     */
    public Page<SensitiveOperationDO> page(int page, int size, String userId, String opType) {
        Page<SensitiveOperationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveOperationDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(SensitiveOperationDO::getUserId, userId);
        if (StringUtils.hasText(opType)) w.eq(SensitiveOperationDO::getBizType, opType);
        w.orderByDesc(SensitiveOperationDO::getVerifiedAt);
        return mapper.selectPage(p, w);
    }

    /**
     * 按用户查询敏感操作历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 敏感操作列表
     */
    public List<SensitiveOperationDO> listByUser(String userId, int limit) {
        return mapper.selectByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 根据 ID 查询敏感操作
     *
     * @param id 记录 ID
     * @return 敏感操作实体
     */
    public SensitiveOperationDO getById(String id) {
        return mapper.selectById(id);
    }
}