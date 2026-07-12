package com.njydsz.pmis.project.server.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.njydsz.pmis.common.datasource.DataSourceConstants;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.infra.mapper.InitiationMapper;
import com.njydsz.pmis.project.domain.query.ProjectSearchVO;
import com.njydsz.pmis.project.domain.query.UniversalSearchVO;
import com.njydsz.pmis.project.server.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

    /** 立项 Mapper（全文检索） */
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

    /**
     * 跨实体统一搜索 (P2-1)。
     *
     * <p>当前实现：聚合项目搜索结果，后续可通过 Feign 扩展到合同/审批/工单/人员/知识库。
     * 任何子搜索异常均降级跳过，不影响其他实体类型的搜索结果。
     *
     * @param keyword 搜索关键词
     * @param size    每类实体最大返回条数
     * @return 统一搜索结果列表
     */
    @Override
    public List<UniversalSearchVO> searchAll(String keyword, int size) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        List<UniversalSearchVO> results = new ArrayList<>();

        // 1. 项目搜索 (复用现有 PG tsvector 检索)
        try {
            Page<ProjectSearchVO> projectPage = searchProjects(
                    keyword, PageRequest.of(0, size));
            for (ProjectSearchVO p : projectPage.getContent()) {
                results.add(UniversalSearchVO.builder()
                        .type("project")
                        .id(p.getId())
                        .title(p.getProjectName())
                        .subtitle(joinNonBlank(p.getCustomerName(), p.getPmName()))
                        .status(p.getStage())
                        .path("/project/initiation?highlight=" + p.getId())
                        .build());
            }
        } catch (Exception e) {
            log.warn("[Search] 项目搜索失败，跳过: keyword={}, error={}", keyword, e.getMessage());
        }

        // 2. 后续可通过 Feign 调用其他微服务扩展搜索范围
        // - 合同: feignClient.searchContracts(keyword, size)
        // - 审批: feignClient.searchApprovals(keyword, size)
        // - 工单: feignClient.searchTickets(keyword, size)
        // - 人员: feignClient.searchEmployees(keyword, size)
        // - 知识库: feignClient.searchKnowledge(keyword, size)

        return results;
    }

    /** 拼接非空字符串，用 ' · ' 分隔 */
    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
