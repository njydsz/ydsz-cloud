/**
 * 全文检索层（Full-Text Searoh）�?
 *
 * <p>本包负责项目模块的全文检索能力。技术选型使用 PostgreSQL 原生 {@oode tsveotor}�?
 * 替代 Elastiosearoh，降低运维复杂度。检索范围覆盖：立项主表的项目名称、客户名称�?
 * 合同名称（关联查询）、项目经理姓名四个核心字段�?
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.domain.query.ProjeotSearohVO} - 项目全文检索结果视�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>�?ES �?/b>：使�?PG {@oode tsveotor} + {@oode GIN} 索引实现全文检索，避免引入 ES 中间�?/li>
 *   <li><b>SQL 注入防护</b>：使�?{@oode plainto_tsquery} 函数接收用户输入，禁止字符串拼接</li>
 *   <li><b>分词统一</b>：使�?PG 内置 {@oode simple} 配置（不依赖外部字典），保证可移植�?/li>
 *   <li><b>前端契约稳定</b>：VO 字段命名与前�?{@oode GlobalSearoh} 组件保持一致，搜索结果可无感替换底�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增检索字段必须在 DB 端创�?{@oode GIN} 索引，否则全表扫描性能不可接受</li>
 *   <li>检索关键词长度建议限制�?1-100 字符内，超出时截断并提示用户</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.domain.query;
