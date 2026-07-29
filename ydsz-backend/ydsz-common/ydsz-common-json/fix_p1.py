import pathlib

f = pathlib.Path('ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/YdszJsonMapper.java')
content = f.read_text(encoding='utf-8')

# P1-2: Add new API methods before ASM warmup section
old1 = '    // ==================== ASM 预热 ===================='
new1 = '''    // ==================== 类型转换 API ====================

    /**
     * 将对象从一种类型转换为另一种类型（对标 Jackson ObjectMapper.convertValue）。
     *
     * <p>通过序列化 -> 反序列化管道实现类型转换。</p>
     *
     * @param fromValue 源对象
     * @param toValueType 目标类型
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        String json = toJson(fromValue);
        return toObject(json, toValueType);
    }

    /**
     * 将对象从一种类型转换为另一种泛型类型（对标 Jackson ObjectMapper.convertValue）。
     *
     * @param fromValue 源对象
     * @param toValueTypeRef 目标类型引用
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T convertValue(Object fromValue, YdszJsonType<T> toValueTypeRef) {
        if (fromValue == null) {
            return null;
        }
        String json = toJson(fromValue);
        return toObject(json, toValueTypeRef);
    }

    /**
     * 将 JsonNode 树转换为指定类型的对象（对标 Jackson ObjectMapper.treeToValue）。
     *
     * @param node JsonNode 树
     * @param clazz 目标类型
     * @param <T> 目标类型参数
     * @return 转换后的对象
     * @since 1.3.0
     */
    public <T> T treeToValue(JsonNode node, Class<T> clazz) {
        if (node == null) {
            return null;
        }
        String json = node.toString();
        return toObject(json, clazz);
    }

    /**
     * 序列化对象为 JSON 字符串（对标 Jackson ObjectMapper.writeValueAsString）。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @since 1.3.0
     */
    public String writeValueAsString(Object obj) {
        return toJson(obj);
    }

    /**
     * 序列化对象为 UTF-8 字节数组（对标 Jackson ObjectMapper.writeValueAsBytes）。
     *
     * @param obj 要序列化的对象
     * @return UTF-8 编码的字节数组
     * @since 1.3.0
     */
    public byte[] writeValueAsBytes(Object obj) {
        return toJsonBytes(obj);
    }

    /**
     * 从 JSON 字符串读取指定类型的对象（对标 Jackson ObjectMapper.readValue）。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @since 1.3.0
     */
    public <T> T readValue(String json, Type type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        validateJsonSize(json);
        return recordDeserialize(() -> DeserializationProvider.deserialize(json, type));
    }

    /**
     * 格式化输出 JSON 字符串（美化模式）。
     *
     * @param obj 要序列化的对象
     * @return 格式化的 JSON 字符串
     * @since 1.3.0
     */
    public String format(Object obj) {
        return toJson(obj, true);
    }

    // ==================== ASM 预热 ===================='''

if old1 in content:
    content = content.replace(old1, new1, 1)
    print('P1-2: New API methods added')
else:
    print('P1-2: old1 NOT FOUND')

# P1-4: Extract recordOperation to eliminate duplicate code
old2 = '''    /**
     * 序列化操作的指标监控包装（与 YdszJson.recordSerialize 逻辑一致）。
     */
    private static <T> T recordSerialize(ThrowingSupplier<T> supplier) {
        JsonMetricsCallback cb = YdszJson.getMetricsCallback();
        if (cb == null) {
            try {
                return supplier.get();
            } catch (Exception e) {
                if (e instanceof YdszJsonException) {
                    throw (YdszJsonException) e;
                }
                throw new YdszJsonException("JSON serialize failed: " + e.getMessage(), e);
            }
        }
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            cb.onSerializeSuccess(System.nanoTime() - start);
            return result;
        } catch (Exception e) {
            cb.onSerializeFailure();
            if (e instanceof YdszJsonException) {
                throw (YdszJsonException) e;
            }
            throw new YdszJsonException("JSON serialize failed: " + e.getMessage(), e);
        }
    }

    /**
     * 反序列化操作的指标监控包装（与 YdszJson.recordDeserialize 逻辑一致）。
     */
    private static <T> T recordDeserialize(ThrowingSupplier<T> supplier) {
        JsonMetricsCallback cb = YdszJson.getMetricsCallback();
        if (cb == null) {
            try {
                return supplier.get();
            } catch (Exception e) {
                if (e instanceof YdszJsonException) {
                    throw (YdszJsonException) e;
                }
                throw new YdszJsonException("JSON deserialize failed: " + e.getMessage(), e);
            }
        }
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            cb.onDeserializeSuccess(System.nanoTime() - start);
            return result;
        } catch (Exception e) {
            cb.onDeserializeFailure();
            if (e instanceof YdszJsonException) {
                throw (YdszJsonException) e;
            }
            throw new YdszJsonException("JSON deserialize failed: " + e.getMessage(), e);
        }
    }'''

new2 = '''    /**
     * 序列化/反序列化操作的指标监控包装（统一逻辑，消除重复代码）。
     *
     * @param supplier 操作供应商
     * @param isSerialize 是否为序列化操作
     * @return 操作结果
     */
    private static <T> T recordOperation(ThrowingSupplier<T> supplier, boolean isSerialize) {
        JsonMetricsCallback cb = YdszJson.getMetricsCallback();
        if (cb == null) {
            try {
                return supplier.get();
            } catch (Exception e) {
                if (e instanceof YdszJsonException) {
                    throw (YdszJsonException) e;
                }
                throw new YdszJsonException(
                    (isSerialize ? "JSON serialize failed: " : "JSON deserialize failed: ")
                    + e.getMessage(), e);
            }
        }
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            if (isSerialize) {
                cb.onSerializeSuccess(System.nanoTime() - start);
            } else {
                cb.onDeserializeSuccess(System.nanoTime() - start);
            }
            return result;
        } catch (Exception e) {
            if (isSerialize) {
                cb.onSerializeFailure();
            } else {
                cb.onDeserializeFailure();
            }
            if (e instanceof YdszJsonException) {
                throw (YdszJsonException) e;
            }
            throw new YdszJsonException(
                (isSerialize ? "JSON serialize failed: " : "JSON deserialize failed: ")
                + e.getMessage(), e);
        }
    }

    /**
     * 序列化操作的指标监控包装。
     */
    private static <T> T recordSerialize(ThrowingSupplier<T> supplier) {
        return recordOperation(supplier, true);
    }

    /**
     * 反序列化操作的指标监控包装。
     */
    private static <T> T recordDeserialize(ThrowingSupplier<T> supplier) {
        return recordOperation(supplier, false);
    }'''

if old2 in content:
    content = content.replace(old2, new2, 1)
    print('P1-4: recordSerialize/recordDeserialize dedup applied')
else:
    print('P1-4: old2 NOT FOUND')

f.write_text(content, encoding='utf-8')
print('File saved')
