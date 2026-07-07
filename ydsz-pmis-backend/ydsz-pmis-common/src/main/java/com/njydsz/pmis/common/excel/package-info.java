/**
 * Excel 导入导出工具层。
 *
 * <p>基于 EasyExcel 封装的导入导出工具，支持：
 * <ul>
 *   <li>注解驱动（{@link com.alibaba.excel.annotation.ExcelProperty}）</li>
 *   <li>大数据量分批读写（{@code ReadListener}）</li>
 *   <li>动态表头（运行时构建 {@code List<List<String>>}）</li>
 *   <li>模板导出（基于预置 .xlsx 模板）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>导出方法统一走 {@code exportExecutor} 线程池（{@code AsyncExecutorNames.EXPORT}），
 *       避免阻塞 Web 线程</li>
 *   <li>大文件导出（&gt;10w 行）应走异步导出（{@code AsyncExportService}），生成临时文件后推送下载链接</li>
 *   <li>敏感字段导出需要 {@code @DataExportAudit} 注解审计</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.excel;
