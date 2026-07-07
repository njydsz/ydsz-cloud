package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.DataExportAuditDO;
import com.njydsz.pmis.system.mapper.DataExportAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据导出审计服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportAuditServiceImpl {

    private final DataExportAuditMapper mapper;

    /**
     * 分页查询导出审计
     *
     * @param page   页码
     * @param size   每页大小
     * @param userId 用户 ID（可选）
     * @param module 导出模块（可选）
     * @return 分页结果
     */
    public Page<DataExportAuditDO> page(int page, int size, String userId, String module) {
        Page<DataExportAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<DataExportAuditDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(DataExportAuditDO::getUserId, userId);
        if (StringUtils.hasText(module)) w.eq(DataExportAuditDO::getExportModule, module);
        w.orderByDesc(DataExportAuditDO::getExportedAt);
        return mapper.selectPage(p, w);
    }

    /**
     * 按用户查询导出历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 导出审计列表
     */
    public List<DataExportAuditDO> listByUser(String userId, int limit) {
        return mapper.selectByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 根据 ID 查询导出审计
     *
     * @param id 记录 ID
     * @return 导出审计实体
     */
    public DataExportAuditDO getById(String id) {
        return mapper.selectById(id);
    }
}