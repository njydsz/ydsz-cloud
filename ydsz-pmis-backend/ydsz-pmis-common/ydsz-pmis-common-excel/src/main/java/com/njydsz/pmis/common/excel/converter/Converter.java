package com.njydsz.pmis.common.excel.converter;

/**
 * Excel数据转换器接口
 *
 * <p>用于自定义字段与Excel单元格之间的转换逻辑。
 * 实现此接口可以处理复杂的数据类型转换场景。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 自定义转换器 - 性别字段
 * public class GenderConverter implements Converter<String, Integer> {
 *     @Override
 *     public Integer convertToExcel(String source) {
 *         return "男".equals(source) ? 1 : 0;
 *     }
 *
 *     @Override
 *     public String convertFromSource(Integer source) {
 *         return source == 1 ? "男" : "女";
 *     }
 * }
 *
 * // 使用转换器
 * @ExcelProperty(value = "性别", converterClass = GenderConverter.class)
 * private String gender;
 * }</pre>
 *
 * @param <T> 目标Java字段类型
 * @param <S> Excel单元格值类型
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
public interface Converter<T, S> {

    /**
     * 将Java对象转换为Excel单元格值
     *
     * @param source Java对象值
     * @return Excel单元格值
     * @throws Exception 转换异常
     */
    S convertToExcel(T source) throws Exception;

    /**
     * 将Excel单元格值转换为Java对象
     *
     * @param source Excel单元格值
     * @return Java对象值
     * @throws Exception 转换异常
     */
    T convertFromSource(S source) throws Exception;
}
