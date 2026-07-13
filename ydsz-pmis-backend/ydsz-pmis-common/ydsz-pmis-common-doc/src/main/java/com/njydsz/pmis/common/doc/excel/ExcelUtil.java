package com.njydsz.pmis.common.doc.excel;

import com.alibaba.excel.EasyExcel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 读取工具类。
 *
 * <p>封装 EasyExcel 的读取逻辑，提供简洁的 API。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ExcelUtil {

    private ExcelUtil() {
    }

    /**
     * 读取 Excel 文件全部数据。
     *
     * @param file 上传的 Excel 文件
     * @param clazz 目标数据类型
     * @param <T>   数据类型
     * @return 数据列表；读取失败返回空列表
     */
    public static <T> List<T> readAll(MultipartFile file, Class<T> clazz) {
        if (file == null || file.isEmpty()) {
            return new ArrayList<>();
        }
        try (InputStream in = file.getInputStream()) {
            return EasyExcel.read(in, clazz).sheet().doReadSync();
        } catch (IOException e) {
            throw new RuntimeException("读取 Excel 文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从输入流读取 Excel 全部数据。
     *
     * @param in    输入流
     * @param clazz 目标数据类型
     * @param <T>   数据类型
     * @return 数据列表
     */
    public static <T> List<T> readAll(InputStream in, Class<T> clazz) {
        return EasyExcel.read(in, clazz).sheet().doReadSync();
    }
}
