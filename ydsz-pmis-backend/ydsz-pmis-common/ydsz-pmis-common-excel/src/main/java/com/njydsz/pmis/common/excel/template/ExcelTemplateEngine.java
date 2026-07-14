package com.njydsz.pmis.common.excel.template;

import java.util.List;
import java.util.Map;

public interface ExcelTemplateEngine {
    byte[] render(String templatePath, Map<String, Object> context);
    byte[] render(String templatePath, Map<String, Object> context, List<Map<String, Object>> listData, String listKey);
}