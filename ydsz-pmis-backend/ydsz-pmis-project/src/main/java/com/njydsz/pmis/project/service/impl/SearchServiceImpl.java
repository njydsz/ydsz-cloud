package com.njydsz.pmis.project.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.njydsz.pmis.common.datasource.DataSourceConstants;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.mapper.InitiationMapper;
import com.njydsz.pmis.project.search.ProjectSearchVO;
import com.njydsz.pmis.project.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 项目全文检索 Service 实现（P2-19：替代 Elasticsearch，改用 PostgreSQL tsvector）。
 *
 * <p>核心思路：移除 ES 8.x 集群后，全文检索下沉到 PostgreSQL，
 * 利用 PG 原生 {@code to_tsvector('simple', ...)} + {@code plainto_tsquery} 实现关键词匹配。
 *
 * <p>降级策略：任何数据库异常都被捕获并返回空分页，绝不影响主业务链路，
 * 与原 ES 实现的容错契约保持一致。
 *
 * <p>租户隔离：从 {@link TenantContext} 读取当前租户 ID 传入 Mapper，
 * 保证不同租户的数据在 SQL 层就完全隔离（不需要在 Service 层做二次过滤）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceConstants.SLAVE)
public class SearchServiceImpl implements SearchService {

    private final InitiationMapper initiationMapper;

    /**
     * 全文检索项目。
     *
     * <p>执行流程：参数校验 → 租户解析 → count + list 两次查询 → 拼装 Page 结果。
     * 所有异常均降级返回空分页并打印 warn 日志，避免污染调用方业务日志。
     *
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    @Override
    public Page<ProjectSearchVO> searchProjects(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        try {
            String tenantId = TenantContext.getTenantId();
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();
            long total = initiationMapper.countByFullText(keyword, tenantId);
            if (total == 0) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            List<ProjectSearchVO> records = initiationMapper.searchByFullText(keyword, tenantId, offset, limit);
            return new PageImpl<>(records, pageable, total);
        } catch (Exception e) {
            log.warn("[Search] PG tsvector 检索失败，降级返回空结果: keyword={}, error={}",
                    keyword, e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * 重建索引。
     *
     * <p>P2-19：PG tsvector 不需要"重建"操作（写入时通过表达式索引自动维护），
     * 这里返回固定的成功标识以保持 API 兼容性。
     *
     * @return 固定字符串
     */
    @Override
    public String reindexAll() {
        log.info("[Search] PG tsvector 无需重建索引，no-op");
        return "pg-tsvector: no-op";
    }
}
