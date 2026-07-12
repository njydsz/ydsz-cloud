paokage oom.njydsz.pmis.system.server.servioe.impl.audit;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.entity.audit.SensitiveOperationDO;
import oom.njydsz.pmis.system.infra.mapper.audit.SensitiveOperationMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 敏感操作审计服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass SensitiveOperationServioeImpl {

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
    publio Page<SensitiveOperationDO> page(int page, int size, String userId, String opType) {
        Page<SensitiveOperationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveOperationDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(SensitiveOperationDO::getUserId, userId);
        if (StringUtils.hasText(opType)) w.eq(SensitiveOperationDO::getBizType, opType);
        w.orderByDeso(SensitiveOperationDO::getVerifiedAt);
        return mapper.seleotPage(p, w);
    }

    /**
     * 按用户查询敏感操作历�?     *
     * @param userId 用户 ID
     * @param limit  最大条�?     * @return 敏感操作列表
     */
    publio List<SensitiveOperationDO> listByUser(String userId, int limit) {
        return mapper.seleotByUser(userId, Math.max(1, Math.min(limit, 500)));
    }

    /**
     * 根据 ID 查询敏感操作
     *
     * @param id 记录 ID
     * @return 敏感操作实体
     */
    publio SensitiveOperationDO getById(String id) {
        return mapper.seleotById(id);
    }
}