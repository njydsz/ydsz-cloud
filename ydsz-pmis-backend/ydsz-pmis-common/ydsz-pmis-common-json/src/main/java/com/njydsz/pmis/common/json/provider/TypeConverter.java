package com.njydsz.pmis.common.json.provider;

/**
 * 类型转换与值解析工具
 *
 * <p>负责处理 JSON 值到 Java 类型的转换和解析。</p>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
final class TypeConverter {

    private TypeConverter() {
        throw new UnsupportedOperationException();
    }

    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        if (value instanceof Number) {
            Number num = (Number) value;
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            } else if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            } else if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            } else if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            } else if (targetType == short.class || targetType == Short.class) {
                return num.shortValue();
            } else if (targetType == byte.class || targetType == Byte.class) {
                return num.byteValue();
            }
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        }

        return value;
    }

    static String parseStringValue(String json) {
        json = json.trim();
        if (json.equals("null")) {
            return null;
        }
        if (json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
            String inner = json.substring(1, json.length() - 1);
            return unescapeString(inner);
        }
        return json;
    }

    static String unescapeString(String str) {
        int len = str.length();
        boolean hasEscape = false;
        for (int i = 0; i < len; i++) {
            if (str.charAt(i) == '\\' && i + 1 < len) {
                hasEscape = true;
                break;
            }
        }
        if (!hasEscape) {
            return str;
        }

        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < len) {
                i++;
                char escaped = str.charAt(i);
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 < len) {
                            String hex = str.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } else {
                            sb.append(escaped);
                        }
                        break;
                    default: sb.append(escaped); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static int parseIntValue(String json) {
        try {
            return Integer.parseInt(json.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static long parseLongValue(String json) {
        try {
            return Long.parseLong(json.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static double parseDoubleValue(String json) {
        try {
            return Double.parseDouble(json.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    static float parseFloatValue(String json) {
        try {
            return Float.parseFloat(json.trim());
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    static boolean parseBooleanValue(String json) {
        return "true".equalsIgnoreCase(json.trim());
    }
}
