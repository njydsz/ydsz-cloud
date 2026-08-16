package com.njydsz.common.excel.core.listener;

import java.util.List;
import com.njydsz.common.excel.core.context.AnalysisContext;

/**
 * Excel 读取监听器 — 数据读取回调接口
 *
 * <p>用于监听 Excel 读取过程中的关键事件，包括读取开始、数据行读取、读取结束等。 * 每读取一行数据会触发 {@link #onData} 回调，适合大数据量的流式处理场景。</p>
 *
 * <h3>事件触发顺序</h3>
 * <ol>
 *   <li>{@link #onStart} - 读取开始时调用</li>
 *   <li>{@link #onData} - 每读取一行数据调用一次</li>
 *   <li>{@link #onEnd} - 读取结束时调用</li>
 *   <li>{@link #onError} - 发生错误时调用(可选覆盖)</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet()
 *     .doRead(new ReadListener<User>() {
 *         @Override
 *         public void onStart(AnalysisContext context) {
 *             System.out.println("开始读取...");
 *         }
 *
 *         @Override
 *         public void onData(AnalysisContext context, User data) {
 *             System.out.println("读到数据: " + data);
 *         }
 *
 *         @Override
 *         public void onEnd(AnalysisContext context) {
 *             System.out.println("读取完成,共 " + context.getCurrentRow() + " 行");
 *         }
 *     });
 * }</pre>
 *
 * <h3>进度回调示例</h3>
 * <pre>{@code
 * ExcelFacade.read("large.xlsx", User.class)
 *     .progress(tracker -> System.out.println("进度: " + tracker.getProgress() + "%"))
 *     .doRead(listener);
 * }</pre>
 *
 * <h3>批量处理示例</h3>
 * <pre>{@code
 * ExcelFacade.read("large.xlsx", User.class)
 *     .batchSize(1000)  // 每1000条批量回调
 *     .doRead(new ReadListener<List<User>>() {
 *         @Override
 *         public void onStart(AnalysisContext context) {}
 *
 *         @Override
 *         public void onData(AnalysisContext context, List<User> batch) {
 *             batchService.saveBatch(batch);  // 批量保存
 *         }
 *
 *         @Override
 *         public void onEnd(AnalysisContext context) {}
 *     });
 * }</pre>
 *
 * @param <T> 泛型参数,表示映射的数据类型
 * @see ExcelReader
 * @see AnalysisContext
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ReadListener<T> {

    /**
     * 读取开始时调用
     *
     * <p>此时尚未开始解析数据,可在此进行初始化操作,
     * 如创建数据库连接、初始化缓存等。</p>
     *
     * @param context 分析上下文
     */
    void onStart(AnalysisContext context);

    /**
     * 每读取一行数据时调用
     *
     * <p>这是最核心的回调方法,每解析一行Excel数据调用一次。
     * 在此进行数据的处理、存储等业务逻辑。</p>
     *
     * @param context 分析上下文,包含当前行号等信息
     * @param data 读取到的单行数据,类型为T或Map
     */
    void onData(AnalysisContext context, T data);

    /**
     * 读取结束时调用
     *
     * <p>无论读取是否成功都会调用。
     * 可在此进行资源清理、统计汇总等操作。</p>
     *
     * @param context 分析上下文
     */
    void onEnd(AnalysisContext context);

    /**
     * 发生错误时的回调
     *
     * <p>默认实现会抛出RuntimeException。
     * 如需自定义错误处理,可覆盖此方法。</p>
     *
     * @param context 分析上下文
     * @param e 发生的异常
     */
    default void onError(AnalysisContext context, Exception e) {
        throw new RuntimeException("Excel读取异常", e);
    }

    /**
     * 进度回调
     *
     * <p>在大文件读取时可以提供进度信息,方便UI更新或日志记录。
     * 默认实现为空。</p>
     *
     * @param context 分析上下文
     * @param current 当前处理的行号
     * @param total 总行数
     */
    default void onProgress(AnalysisContext context, int current, int total) {
    }

    /**
     * 批量数据回调
     *
     * <p>当使用批量模式读取时,每积累到指定数量调用一次。
     * 适合需要批量处理的场景,如批量入库。</p>
     *
     * <h3>注意</h3>
     * <p>如果使用批量回调,则 {@link #onData} 不会被调用。
     * 默认实现会遍历批量数据逐条调用 {@link #onData}。</p>
     *
     * @param context 分析上下文
     * @param batch 批量数据列表
     */
    default void onBatchData(AnalysisContext context, List<T> batch) {
        for (T item : batch) {
            onData(context, item);
        }
    }

    /**
     * 获取监听器名称
     *
     * <p>用于日志记录和调试。
     * 默认返回简单类名。</p>
     *
     * @return 监听器名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
