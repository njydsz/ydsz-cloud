#!/usr/bin/env python3
"""Restore missing fields and getters/setters in ExcelConfig.java.

The debrand migration script accidentally removed several fields from ExcelConfig.java.
This script restores them from git history (commit e89043da8^).
"""
import pathlib
import subprocess

FILE_PATH = pathlib.Path(
    r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-excel"
    r"\src\main\java\com\njydsz\common\excel\core\config\ExcelConfig.java"
)

# Missing fields to insert after "private int maxReadCacheSize = 1024;"
MISSING_FIELDS = """    /** 写入缓存大小(SXSSFWorkbook行数) - 参考EasyExcel默认值 */
    private int writeCacheSize = 100;

    /** 默认表头行号 */
    private int headRowNumber = 1;

    /** 是否使用1904日期窗口 */
    private boolean use1904Windowing = false;

    /** 是否保留富文本格式 */
    private boolean keepRichTextFormat = true;

    /** 最大Sheet缓存数量 */
    private int maxSheetCacheSize = 10;

    /** 强制使用输入流模式 */
    private boolean mandatoryUseInputStream = false;

    /** 是否写入隐藏Sheet */
    private boolean writeHiddenSheet = false;

    /** 保护密码 */
    private String password;

    /** 是否使用快速解析器（默认开启） */
    private boolean useFastReader = true;

    /** 是否使用快速写入器（默认开启，直接生成XML，性能远超POI方式） */
    private boolean useFastWriter = true;

"""

# Missing getters/setters to insert after setMaxReadCacheSize method
MISSING_GETTERS_SETTERS = """
    public int getWriteCacheSize() {
        return writeCacheSize;
    }

    public void setWriteCacheSize(int writeCacheSize) {
        if (writeCacheSize <= 0) {
            throw new IllegalArgumentException("writeCacheSize must be positive, got: " + writeCacheSize);
        }
        this.writeCacheSize = writeCacheSize;
    }

    public int getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(int headRowNumber) {
        if (headRowNumber < 0) {
            throw new IllegalArgumentException("headRowNumber cannot be negative, got: " + headRowNumber);
        }
        this.headRowNumber = headRowNumber;
    }

    public boolean isUse1904Windowing() {
        return use1904Windowing;
    }

    public void setUse1904Windowing(boolean use1904Windowing) {
        this.use1904Windowing = use1904Windowing;
    }

    public boolean isKeepRichTextFormat() {
        return keepRichTextFormat;
    }

    public void setKeepRichTextFormat(boolean keepRichTextFormat) {
        this.keepRichTextFormat = keepRichTextFormat;
    }

    public int getMaxSheetCacheSize() {
        return maxSheetCacheSize;
    }

    public void setMaxSheetCacheSize(int maxSheetCacheSize) {
        if (maxSheetCacheSize <= 0) {
            throw new IllegalArgumentException("maxSheetCacheSize must be positive, got: " + maxSheetCacheSize);
        }
        this.maxSheetCacheSize = maxSheetCacheSize;
    }

    public boolean isMandatoryUseInputStream() {
        return mandatoryUseInputStream;
    }

    public void setMandatoryUseInputStream(boolean mandatoryUseInputStream) {
        this.mandatoryUseInputStream = mandatoryUseInputStream;
    }

    public boolean isWriteHiddenSheet() {
        return writeHiddenSheet;
    }

    public void setWriteHiddenSheet(boolean writeHiddenSheet) {
        this.writeHiddenSheet = writeHiddenSheet;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseFastReader() {
        return useFastReader;
    }

    public void setUseFastReader(boolean useFastReader) {
        this.useFastReader = useFastReader;
    }

    public boolean isUseFastWriter() {
        return useFastWriter;
    }

    public void setUseFastWriter(boolean useFastWriter) {
        this.useFastWriter = useFastWriter;
    }
"""

content = FILE_PATH.read_text(encoding="utf-8")

# 1. Insert missing fields after maxReadCacheSize field
fields_marker = "    private int maxReadCacheSize = 1024;\n"
if fields_marker in content and "writeCacheSize" not in content:
    content = content.replace(
        fields_marker,
        fields_marker + "\n" + MISSING_FIELDS,
        1
    )
    print("[OK] Inserted missing fields after maxReadCacheSize")
elif "writeCacheSize" in content:
    print("[SKIP] Fields already present")
else:
    print("[FAIL] Could not find fields insertion point")

# 2. Insert missing getters/setters after setMaxReadCacheSize method
# The setMaxReadCacheSize method ends with:
#         this.maxReadCacheSize = maxReadCacheSize;
#     }
getters_marker = """        this.maxReadCacheSize = maxReadCacheSize;
    }
"""
if getters_marker in content and "isUseFastReader" not in content:
    content = content.replace(
        getters_marker,
        getters_marker + MISSING_GETTERS_SETTERS,
        1
    )
    print("[OK] Inserted missing getters/setters after setMaxReadCacheSize")
elif "isUseFastReader" in content:
    print("[SKIP] Getters/setters already present")
else:
    print("[FAIL] Could not find getters/setters insertion point")

FILE_PATH.write_text(content, encoding="utf-8")
print("[DONE] ExcelConfig.java restored")
