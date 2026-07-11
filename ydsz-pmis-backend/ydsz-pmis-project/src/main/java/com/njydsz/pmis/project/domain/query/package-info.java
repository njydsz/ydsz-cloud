/**
 * 全文检索层（Full-Text Search）。
 *
 * <p>本包负责项目模块的全文检索能力。技术选型使用 PostgreSQL 原生 {@code tsvector}，
 * 替代 Elasticsearch，降低运维复杂度。检索范围覆盖：立项主表的项目名称、客户名称、
 * 合同名称（关联查询）、项目经理姓名四个核心字段。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.domain.query.ProjectSearchVO} - 项目全文检索结果视图</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>去 ES 化</b>：使用 PG {@code tsvector} + {@code GIN} 索引实现全文检索，避免引入 ES 中间件</li>
 *   <li><b>SQL 注入防护</b>：使用 {@code plainto_tsquery} 函数接收用户输入，禁止字符串拼接</li>
 *   <li><b>分词统一</b>：使用 PG 内置 {@code simple} 配置（不依赖外部字典），保证可移植性</li>
 *   <li><b>前端契约稳定</b>：VO 字段命名与前端 {@code GlobalSearch} 组件保持一致，搜索结果可无感替换底层</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增检索字段必须在 DB 端创建 {@code GIN} 索引，否则全表扫描性能不可接受</li>
 *   <li>检索关键词长度建议限制在 1-100 字符内，超出时截断并提示用户</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.domain.query;
