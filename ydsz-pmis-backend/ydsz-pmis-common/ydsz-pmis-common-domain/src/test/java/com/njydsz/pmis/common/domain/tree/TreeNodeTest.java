package com.njydsz.pmis.common.domain.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * TreeNode 树节点单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TreeNode 树节点测试")
class TreeNodeTest {

    @Test
    @DisplayName("addChild 应将子节点添加到 children 列表")
    void shouldAddChild() {
        TestNode parent = new TestNode();
        parent.setId("1");
        TestNode child = new TestNode();
        child.setId("2");
        parent.addChild(child);
        assertEquals(1, parent.getChildren().size());
        assertEquals("2", parent.getChildren().get(0).getId());
    }

    @Test
    @DisplayName("isRootNode 在 parentId 为 null 时返回 true")
    void shouldReturnTrueForRootNode() {
        TestNode node = new TestNode();
        node.setId("1");
        assertTrue(node.isRootNode());
    }

    @Test
    @DisplayName("isLeafNode 在 children 为空时返回 true")
    void shouldReturnTrueForLeafNode() {
        TestNode node = new TestNode();
        node.setId("1");
        node.setChildren(new ArrayList<>());
        assertTrue(node.isLeafNode());
    }

    @Test
    @DisplayName("getDescendantCount 应统计所有后代节点数量")
    void shouldCountDescendants() {
        TestNode root = buildTestTree();
        assertEquals(5, root.getDescendantCount());
    }

    @Test
    @DisplayName("getDepth 应返回节点层级深度")
    void shouldReturnDepth() {
        TestNode root = buildTestTree();
        TestNode grandChild = root.getChildren().get(0).getChildren().get(0);
        assertEquals(3, grandChild.getDepth());
    }

    @Test
    @DisplayName("copy 应创建浅拷贝副本")
    void shouldCreateShallowCopy() {
        TestNode original = new TestNode();
        original.setId("1");
        original.setLevel(1);
        TestNode copy = original.copy();
        assertNotNull(copy);
        assertEquals("1", copy.getId());
        assertEquals(1, copy.getLevel());
    }

    @Test
    @DisplayName("cloneSubTree 应递归深拷贝所有子节点")
    void shouldDeepCloneSubTree() {
        TestNode root = buildTestTree();
        TestNode cloned = root.cloneSubTree();
        assertEquals(root.getId(), cloned.getId());
        assertEquals(root.getChildren().size(), cloned.getChildren().size());
        // 确保是深拷贝（不同实例）
        assertTrue(root.getChildren().get(0) != cloned.getChildren().get(0));
    }

    @Test
    @DisplayName("moveTo 应更新 parentId")
    void shouldUpdateParentIdOnMove() {
        TestNode node = new TestNode();
        node.setId("1");
        node.setParentId("0");
        node.moveTo("99");
        assertEquals("99", node.getParentId());
    }

    @Test
    @DisplayName("moveTo(null) 应将节点变为根节点")
    void shouldBecomeRootOnMoveToNull() {
        TestNode node = new TestNode();
        node.setId("1");
        node.setParentId("0");
        node.moveTo(null);
        assertEquals(null, node.getParentId());
        assertEquals(TreeNode.ROOT_LEVEL, node.getLevel());
    }

    private TestNode buildTestTree() {
        TestNode root = new TestNode();
        root.setId("1");
        root.setLevel(1);

        TestNode child1 = new TestNode();
        child1.setId("2");
        child1.setParentId("1");
        child1.setLevel(2);

        TestNode child2 = new TestNode();
        child2.setId("3");
        child2.setParentId("1");
        child2.setLevel(2);

        TestNode grandChild1 = new TestNode();
        grandChild1.setId("4");
        grandChild1.setParentId("2");
        grandChild1.setLevel(3);

        TestNode grandChild2 = new TestNode();
        grandChild2.setId("5");
        grandChild2.setParentId("2");
        grandChild2.setLevel(3);

        TestNode grandGrandChild = new TestNode();
        grandGrandChild.setId("6");
        grandGrandChild.setParentId("4");
        grandGrandChild.setLevel(4);

        grandChild1.addChild(grandGrandChild);
        child1.addChild(grandChild1);
        child1.addChild(grandChild2);
        root.addChild(child1);
        root.addChild(child2);

        return root;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @NoArgsConstructor
    static class TestNode extends TreeNode<TestNode, String> {
        private static final long serialVersionUID = 1L;

        @Override
        protected TestNode newInstance() {
            return new TestNode();
        }
    }
}
