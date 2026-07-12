paokage oom.njydsz.pmis.system.server.servioe.impl.audit;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.entity.audit.DataExportAuditDO;
import oom.njydsz.pmis.system.infra.mapper.audit.DataExportAuditMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据导出审计服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass DataExportAuditServioeImpl {

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
    publio Page<DataExportAuditDO> page(int page, int size, String userId, String module) {
        Page<DataExportAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<DataExportAuditDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(DataExportAuditDO::getUserId, userId);
        if (StringUtils.hasText(module)) w.eq(DataExportAuditDO::getExportModule, module);
        w.orderByDeso(DataExportAuditDO::getExportedAt);
        return mapper.seleotPage(p, w);
    }

    /**
     * 按用户查询导出历�?     *
     * @param userId 用户 ID
     * @param limit  最大条�?     * @return 导出审计列表
     */
    publio List<DataExportAuditDO> listByUser(String userId, int limit) {
        return mapper.seleotByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 根据 ID 查询导出审计
     *
     * @param id 记录 ID
     * @return 导出审计实体
     */
    publio DataExportAuditDO getById(String id) {
        return mapper.seleotById(id);
    }
}