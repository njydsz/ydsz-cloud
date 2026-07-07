package com.njydsz.pmis.project.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.project.entity.RuleDefinitionDO;
import com.njydsz.pmis.project.mapper.RuleDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则目录树 Service（P1-9）
 *
 * <p>提供基于 category_path 的多级目录树构建、按路径过滤规则、按 Owner 筛选等能力。
 * 树形结构：节点包含子节点、规则数、Owner 列表等信息。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleCategoryTreeService {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    /**
     * 构建规则目录树
     *
     * @return 树根（虚拟根，name="ROOT"），children 为一级分类
     */
    public CategoryNode buildTree() {
        List<RuleDefinitionDO> all = ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .isNotNull(RuleDefinitionDO::getCategoryPath));

        // 按 categoryPath 聚合
        // path="a/b/c" → 拆成 [a, a/b, a/b/c] 三个虚拟节点
        CategoryNode root = new CategoryNode("ROOT", "/", 0, true);
        Map<String, CategoryNode> nodeIndex = new LinkedHashMap<>();
        nodeIndex.put("/", root);

        for (RuleDefinitionDO rule : all) {
            String path = rule.getCategoryPath();
            if (path == null || path.isBlank()) continue;
            String[] segments = path.split("/");
            StringBuilder currentPath = new StringBuilder();
            CategoryNode parent = root;
            for (int i = 0; i < segments.length; i++) {
                String seg = segments[i].trim();
                if (seg.isEmpty()) continue;
                if (currentPath.length() > 0) currentPath.append('/');
                currentPath.append(seg);
                String nodePath = currentPath.toString();
                int depth = i + 1;
                CategoryNode node = nodeIndex.get(nodePath);
                if (node == null) {
                    node = new CategoryNode(seg, nodePath, depth, false);
                    nodeIndex.put(nodePath, node);
                    parent.getChildren().add(node);
                }
                node.increaseRuleCount();
                if (rule.getOwner() != null && !rule.getOwner().isBlank()) {
                    node.getOwners().add(rule.getOwner());
                }
                parent = node;
            }
        }
        // 子节点按 path 字典序排序
        sortChildren(root);
        return root;
    }

    /**
     * 按分类路径前缀查询规则（path 前缀匹配，如 path="finance" 匹配 "finance/credit/loan"）
     */
    public List<RuleDefinitionDO> listByCategoryPath(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return ruleDefinitionMapper.selectList(null);
        }
        return ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .likeRight(RuleDefinitionDO::getCategoryPath, pathPrefix));
    }

    /**
     * 按分类路径前缀查询规则（返回 API Definition）
     */
    public List<RuleDefinition> listDefinitionsByCategoryPath(String pathPrefix) {
        return listByCategoryPath(pathPrefix).stream()
                .map(this::toDefinition)
                .toList();
    }

    /**
     * 按 Owner 查询规则
     */
    public List<RuleDefinitionDO> listByOwner(String owner) {
        if (owner == null || owner.isBlank()) return Collections.emptyList();
        return ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getOwner, owner));
    }

    /**
     * 按 Owner 查询规则（返回 API Definition）
     */
    public List<RuleDefinition> listDefinitionsByOwner(String owner) {
        return listByOwner(owner).stream()
                .map(this::toDefinition)
                .toList();
    }

    private RuleDefinition toDefinition(RuleDefinitionDO d) {
        return RuleDefinition.builder()
                .code(d.getRuleCode())
                .name(d.getRuleName())
                .category(d.getCategory())
                .categoryPath(d.getCategoryPath())
                .owner(d.getOwner())
                .description(d.getDescription())
                .conditionExpression(d.getConditionExpression())
                .severityExpression(d.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromCode(d.getDefaultSeverity()))
                .titleTemplate(d.getTitleTemplate())
                .descriptionTemplate(d.getDescriptionTemplate())
                .priority(d.getPriority() != null ? d.getPriority() : 100)
                .enabled(d.getEnabled() != null ? d.getEnabled() : true)
                .scope(d.getScope())
                .drilldownAvailable(d.getDrilldownAvailable() != null ? d.getDrilldownAvailable() : true)
                .version(d.getVersion() != null ? d.getVersion() : 1)
                .tenantId(d.getTenantId() != null ? d.getTenantId() : "1")
                .status(d.getStatus() != null ? d.getStatus() : "PUBLISHED")
                .build();
    }

    private void sortChildren(CategoryNode node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) return;
        // 子节点按 path 字典序
        node.getChildren().sort((a, b) -> a.getPath().compareTo(b.getPath()));
        for (CategoryNode c : node.getChildren()) {
            sortChildren(c);
        }
    }

    /**
     * 目录树节点
     */
    @lombok.Data
    public static class CategoryNode implements Serializable {
        @Serial
        private static final String serialVersionUID = "1";

        private String name;
        private String path;
        private int depth;
        private boolean root;
        private int ruleCount;
        private List<String> owners = new ArrayList<>();
        private List<CategoryNode> children = new ArrayList<>();

        public CategoryNode() {
        }

        public CategoryNode(String name, String path, int depth, boolean root) {
            this.name = name;
            this.path = path;
            this.depth = depth;
            this.root = root;
        }

        public void increaseRuleCount() {
            this.ruleCount++;
        }
    }
}
