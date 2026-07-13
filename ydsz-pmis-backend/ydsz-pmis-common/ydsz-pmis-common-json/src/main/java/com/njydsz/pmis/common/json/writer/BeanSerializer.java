package com.njydsz.pmis.common.json.writer;

import com.njydsz.pmis.common.json.cache.FieldMeta;
import com.njydsz.pmis.common.json.number.NumberUtils;

import java.lang.invoke.MethodHandle;

/**
 * Bean 专用序列化器
 * 
 * <p>为每个 Bean 类预计算字段元数据，使用 char[] 直接写入，消除运行时类型检查</p>
 * 
 * @author YdszJson Team
 */
public final class BeanSerializer {
    
    /** Bean 类 */
    public final Class<?> clazz;
    
    /** 字段数量 */
    public final int fieldCount;
    
    /** 字段序列化信息 */
    public final FieldWriter[] fields;
    
    /** 预估 JSON 大小 */
    public final int estimatedSize;
    
    /**
     * 构造函数
     */
    public BeanSerializer(Class<?> clazz, FieldMeta[] fieldMetas) {
        this.clazz = clazz;
        
        // 计算有效字段数量
        int count = 0;
        for (FieldMeta meta : fieldMetas) {
            if (!meta.shouldSkip()) {
                count++;
            }
        }
        
        this.fieldCount = count;
        this.fields = new FieldWriter[count];
        int estimatedSize = 2; // {}
        
        int idx = 0;
        for (FieldMeta meta : fieldMetas) {
            if (meta.shouldSkip()) {
                continue;
            }
            
            this.fields[idx++] = new FieldWriter(meta);
            estimatedSize += meta.jsonKeyLen + 16; // 键名 + 平均字段值
        }
        
        this.estimatedSize = estimatedSize;
    }
    
    /**
     * 字段写入器
     */
    public static final class FieldWriter {
        
        /** 字段访问器 */
        public final MethodHandle getter;
        
        /** JSON 键名（含引号） */
        public final String jsonKey;
        
        /** JSON 键名长度 */
        public final int jsonKeyLen;
        
        /** 字段类型 */
        public final Class<?> type;
        
        /** 类型代码 */
        public final int typeCode;
        
        /**
         * 构造函数
         */
        public FieldWriter(FieldMeta meta) {
            this.getter = meta.getter;
            this.jsonKey = meta.jsonKey;
            this.jsonKeyLen = meta.jsonKey.length();
            this.type = meta.type;
            this.typeCode = meta.serializeTypeCode;
        }
    }
    
    /**
     * 序列化对象到 JSONWriter
     */
    public void write(Object obj, JSONWriter writer) {
        writer.ensureCapacity(estimatedSize);
        
        char[] buf = writer.buf;
        int pos = writer.pos;
        
        // 写入 {
        buf[pos++] = '{';
        
        boolean first = true;
        
        for (int i = 0; i < fieldCount; i++) {
            FieldWriter field = fields[i];
            
            switch (field.typeCode) {
                case 1: // String
                    String strVal;
                    try {
                        strVal = (String) field.getter.invoke(obj);
                    } catch (Throwable e) {
                        strVal = null;
                    }
                    if (strVal != null) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;
                        
                        // 写入键名
                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;
                        
                        // 写入字符串值
                        int len = strVal.length();
                        buf[pos++] = '"';
                        
                        // 快速路径：检查转义
                        boolean needsEscape = false;
                        for (int j = 0; j < len; j++) {
                            char c = strVal.charAt(j);
                            if (c < ' ' || c == '"' || c == '\\') {
                                needsEscape = true;
                                break;
                            }
                        }
                        
                        if (!needsEscape) {
                            strVal.getChars(0, len, buf, pos);
                            pos += len;
                        } else {
                            // 需要转义，写入并更新 pos
                            pos = writeStringWithEscape(strVal, buf, pos);
                        }
                        
                        buf[pos++] = '"';
                    }
                    break;
                    
                case 2: // int/Integer
                    int intVal;
                    try {
                        Integer val = (Integer) field.getter.invoke(obj);
                        intVal = val == null ? 0 : val;
                    } catch (Throwable e) {
                        intVal = 0;
                    }
                    if (intVal != 0 || field.type == int.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;
                        
                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;
                        
                        pos += NumberUtils.writeInt(intVal, buf, pos);
                    }
                    break;
                    
                case 3: // long/Long
                    long longVal;
                    try {
                        Long val = (Long) field.getter.invoke(obj);
                        longVal = val == null ? 0L : val;
                    } catch (Throwable e) {
                        longVal = 0L;
                    }
                    if (longVal != 0L || field.type == long.class) {
                        if (!first) {
                            buf[pos++] = ',';
                        }
                        first = false;
                        
                        int keyLen = field.jsonKeyLen;
                        field.jsonKey.getChars(0, keyLen, buf, pos);
                        pos += keyLen;
                        
                        pos += NumberUtils.writeLong(longVal, buf, pos);
                    }
                    break;
                    
                default:
                    Object value;
                    try {
                        value = field.getter.invoke(obj);
                    } catch (Throwable e) {
                        value = null;
                    }
                    if (value == null) {
                        break;
                    }
                    if (!first) {
                        buf[pos++] = ',';
                    }
                    first = false;
                    
                    int keyLen = field.jsonKeyLen;
                    field.jsonKey.getChars(0, keyLen, buf, pos);
                    pos += keyLen;
                    
                    writer.pos = pos;
                    writer.write(value.toString());
                    pos = writer.pos;
                    break;
            }
        }
        
        // 写入 }
        buf[pos++] = '}';
        writer.pos = pos;
    }
    
    /**
     * 写入带转义的字符串
     */
    private static int writeStringWithEscape(String str, char[] buf, int pos) {
        int len = str.length();
        
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    buf[pos++] = '\\';
                    buf[pos++] = '"';
                    break;
                case '\\':
                    buf[pos++] = '\\';
                    buf[pos++] = '\\';
                    break;
                case '\n':
                    buf[pos++] = '\\';
                    buf[pos++] = 'n';
                    break;
                case '\r':
                    buf[pos++] = '\\';
                    buf[pos++] = 'r';
                    break;
                case '\t':
                    buf[pos++] = '\\';
                    buf[pos++] = 't';
                    break;
                default:
                    if (c < ' ') {
                        buf[pos++] = '\\';
                        buf[pos++] = 'u';
                        buf[pos++] = '0';
                        buf[pos++] = '0';
                        char h = (char) (c >> 4);
                        char l = (char) (c & 0xf);
                        buf[pos++] = (char) (h < 10 ? h + '0' : h - 10 + 'a');
                        buf[pos++] = (char) (l < 10 ? l + '0' : l - 10 + 'a');
                    } else {
                        buf[pos++] = c;
                    }
                    break;
            }
        }
        
        return pos;
    }
}
