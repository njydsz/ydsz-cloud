package com.njydsz.project.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.repository.project.IProjectInitiationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目搜索提供者 — 将项目立项数据注册到统一搜索体系
 *
 * <p>将 {@link ProjectInitiation} 注册到 {@link com.njydsz.common.search.core.UnifiedSearchService}，
 * 支持项目名称、客户名称、项目描述的全文搜索与聚合分析。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>声明搜索字段 schema（{@code title} / {@code subtitle} / {@code content} / {@code status}）</li>
 *   <li>提供文档数据源：从 {@code IProjectInitiationRepository} 按需加载实体</li>
 *   <li>字段映射：项目名称 → title，客户名称 → subtitle，描述 → content，项目编号 → snippet</li>
 * </ul>
 *
 * <p><b>字段映射与权重：</b>
 * <ul>
 *   <li>{@code title}（项目名称）— {@code TEXT} 类型，权重 3.0，可高亮、可排序</li>
 *   <li>{@code subtitle}（客户名称）— {@code TEXT} 类型，权重 2.0，可高亮</li>
 *   <li>{@code content}（项目描述）— {@code TEXT} 类型，权重 1.0，可搜索</li>
 *   <li>{@code status}（项目阶段）— {@code KEYWORD} 类型，不可搜索，可聚合</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项实体
 * @see SearchProvider 统一搜索 Provider 接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchProvider implements SearchProvider<ProjectInitiation> {

    private final IProjectInitiationRepository projectInitiationRepository;

    /**
     * 获取搜索类型标识
     *
     * @return 固定返回 {@code "project"}，作为 {@link IndexDocument#type} 字段值
     */
    @Override
    public String getType() {
        return "project";
    }

    /**
     * 获取搜索类型中文标签
     *
     * @return 固定返回 {@code "项目"}，用于前端搜索结果分类展示
     */
    @Override
    public String getTypeLabel() {
        return "项目";
    }

    /**
     * 将 {@link ProjectInitiation} 实体转换为搜索索引文档
     *
     * <p>字段映射规则：
     * <ul>
     *   <li>{@code projectName} → {@code title}（用于列表展示 + 高亮）</li>
     *   <li>{@code customerName} → {@code subtitle}（用于展示客户信息）</li>
     *   <li>{@code description} → {@code content}（用于全文搜索）</li>
     *   <li>{@code projectCode} → {@code snippet}（用于搜索结果摘要）</li>
     *   <li>{@code stage} → {@code status}（用于状态聚合）</li>
     *   <li>{@code path} 固定为 {@code /project/initiation/{id}}，点击搜索结果跳转项目详情页</li>
     * </ul>
     *
     * @param entity 项目立项实体（不可为 null，且必须包含 ID）
     * @return 索引文档；入参为 null 或 ID 为空时返回 null
     */
    @Override
    public IndexDocument toIndexDocument(ProjectInitiation entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return IndexDocument.builder()
                .id(entity.getId())
                .type("project")
                .title(entity.getProjectName())
                .subtitle(entity.getCustomerName())
                .content(entity.getDescription())
                .snippet(entity.getProjectCode())
                .status(entity.getStage())
                .path("/project/initiation/" + entity.getId())
                .tenantId(entity.getTenantId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt() != null
                        ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt() != null
                        ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .build();
    }

    /**
     * 声明可搜索字段 schema
     *
     * <p>四个搜索字段：
     * <ul>
     *   <li><b>title</b>（项目名称）— {@code TEXT} 类型，权重 3.0，可高亮、可排序</li>
     *   <li><b>subtitle</b>（客户名称）— {@code TEXT} 类型，权重 2.0，可高亮</li>
     *   <li><b>content</b>（项目描述）— {@code TEXT} 类型，权重 1.0</li>
     *   <li><b>status</b>（项目阶段）— {@code KEYWORD} 类型，<b>不可搜索</b>，可聚合</li>
     * </ul>
     * <p>权重设计：title &gt; subtitle &gt; content &gt; status，关键字命中项目名称比命中描述分数高。
     *
     * @return 可搜索字段列表
     */
    @Override
    public List<SearchField> getSearchableFields() {
        return List.of(
                SearchField.builder()
                        .name("title").label("项目名称").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("客户名称").type(FieldType.TEXT)
                        .weight(2.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("项目描述").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("项目阶段").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    /**
     * 按 ID 加载项目立项实体
     *
     * <p>全量重建索引时由索引服务调用，用于逐条加载实体并转换为索引文档。
     *
     * @param id 项目立项 ID
     * @return 项目立项实体；不存在时返回 null
     */
    @Override
    public ProjectInitiation loadById(String id) {
        return projectInitiationRepository.getById(id);
    }
}
