package com.njydsz.pmis.common.file.domain;

import java.util.List;

import lombok.Data;

/**
 * 目录树节点模型
 * <p>用于前端组件（如 zTree / Element UI Tree 等）渲染文件树结构。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
public class DirectoryTree {

    /**
     * 节点图标 CSS 类名
     */
    private String iconClass;

    /**
     * 复选框选中状态标识
     */
    private String checkArr = "0";

    /**
     * 节点显示名称
     */
    private String title;

    /**
     * 节点是否默认展开
     */
    private Boolean spread = true;

    /**
     * 子节点列表
     */
    private List<DirectoryTree> children;
}
