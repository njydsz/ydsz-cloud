<!--
================================================================================
PMIS 仓库 · IDE AI 助手规则定义
--------------------------------------------------------------------------------
文件:      .trae/rules/git-commit-message.md
作用域:    全仓库（alwaysApply: true）
生效场景:  IDE AI 助手自动生成 git commit message 时

规则内容:
  使用中文生成提交信息。

适用范围:
  - Commit message 主题行
  - Commit message 正文（body）
  - 提交说明（commit description / footer）

补充说明（PMIS 规范）:
  - 推荐遵循 Conventional Commits:  feat: / fix: / docs: / refactor: / test: / chore: 等
  - 主题行 ≤ 50 中文字符，正文每行 ≤ 72 字符
  - 引用 Issue / MR:  #123  (Jira / GitLab 编号均可)
  - 详细规范见: docs/standards/git-workflow.md
================================================================================
-->
---
alwaysApply: true
scene: git_message
---

使用中文生成提交信息。
