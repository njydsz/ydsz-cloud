package com.njydsz.common.excel.core.postprocess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Xlsx 后处理器函数式接口 — 在写入器完成基础 xlsx 输出后，
 * 允许对 ZIP 包内容进行读-改-写变换（如注入合并单元格、数据验证、条件格式等）。
 *
 * <p>实现类/lambda 接收输入流（刚写出的 xlsx 字节）和输出流（最终结果），
 * 可以将变换后的字节写入输出流。输入流/输出流均由框架管理，实现无需关闭它们。
 *
 * <p>线程安全性：后处理器在写入线程内串行调用，不要求线程安全。
 *
 * <p>内置实现：
 *
 * <ul>
 *   <li>{@link MergeCellHelper} — 扫描连续相同值注入 {@code <mergeCells>}</li>
 *   <li>{@link DataValidationHelper} — 注入 {@code <dataValidations>}</li>
 *   <li>{@link ConditionalFormattingHelper} — 注入 {@code <conditionalFormatting>}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
@FunctionalInterface
public interface XlsxPostProcessor {

  /**
   * 对输入的 xlsx 字节流进行变换，将结果写入输出流。
   *
   * @param input  输入流（刚生成的 xlsx ZIP 内容），由框架关闭
   * @param output 输出流（最终 ZIP 目标），由框架关闭
   * @throws IOException 读取/写入/变换失败时抛出
   */
  void process(InputStream input, OutputStream output) throws IOException;
}
