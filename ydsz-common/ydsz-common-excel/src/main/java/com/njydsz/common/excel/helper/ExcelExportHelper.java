package com.njydsz.common.excel.helper;

import java.io.ByteArrayOutputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;

/**
 * 统一 Excel 导出辅助类 — 封装 common-excel 的导出能力。
 *
 * <p>P1-5: 消除各模块自建导出逻辑的重复编码，
 * 提供统一的导出入口，支持数据导出。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Resource
 * private ExcelExportHelper exportHelper;
 *
 * // 导出为字节数组
 * byte[] bytes = exportHelper.export("用户列表", UserVO.class, userList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ExcelExportHelper {

    /**
     * 导出数据为 Excel 字节数组。
     *
     * @param sheetName Sheet 名称
     * @param dataClass 数据类型（需有 @ExcelProperty 注解）
     * @param dataList  数据列表
     * @return Excel 文件字节数组
     */
    public <T> byte[] export(String sheetName, Class<T> dataClass, List<T> dataList) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelWriter writer = ExcelFacade.write(out, dataClass)
                    .sheet(sheetName);
            writer.doWrite(dataList);
            writer.finish();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[ExcelExportHelper] 导出失败: sheet={}, error={}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出数据为 Excel 字节数组（默认 Sheet 名）。
     *
     * @param dataClass 数据类型
     * @param dataList  数据列表
     * @return Excel 文件字节数组
     */
    public <T> byte[] export(Class<T> dataClass, List<T> dataList) {
        return export("Sheet1", dataClass, dataList);
    }
}
