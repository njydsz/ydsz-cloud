package com.njydsz.literule.server.spi;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.literule.api.RuleDefinition;

import lombok.Data;

/**
 * 规则目录树提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供基于 category_path 的多级目录树构建、
 * 按路径过滤规则、按 Owner 筛选等能力。将原有 {@code RuleCategoryTreeService} 的能力抽象为 SPI，
 * 避免 literule 模块直接依赖 project 模块。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface RuleCategoryProvider {

    /**
     * 构建规则目录树
     *
     * @return 树根（虚拟根，name="ROOT"），children 为一级分类
     */
    CategoryNode buildTree();

    /**
     * 按分类路径前缀查询规则（返回 API Definition）
     *
     * @param pathPrefix 分类路径前缀，为空时返回全部
     * @return 规则定义列表
     */
    List<RuleDefinition> listDefinitionsByCategoryPath(String pathPrefix);

    /**
     * 按 Owner 查询规则（返回 API Definition）
     *
     * @param owner 责任人
     * @return 规则定义列表
     */
    List<RuleDefinition> listDefinitionsByOwner(String owner);

    /**
     * 目录树节点
     */
    @Data
    class CategoryNode implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

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

        /**
         * 累加本目录节点的规则计数（构建目录树时每挂接一条规则调用一次）。
         *
         * <p>仅作统计聚合，不影响规则本身的注册与评估；计数仅供前端目录树展示用。
         */
        public void increaseRuleCount() {
            this.ruleCount++;
        }
    }
}
