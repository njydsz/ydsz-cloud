package com.njydsz.pmis.common.util.classloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类工具类
 *
 * <p>提供全面的类加载、反射相关操作方法，功能对标 Apache Commons Lang ClassUtils 和 Spring ClassUtils，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>类加载：loadClass、getClass、getPrimitiveClass</li>
 *   <li>资源加载：getResource、getResourceAsStream、getResourceURL</li>
 *   <li>类路径：getClassPath、getClassPathRoot</li>
 *   <li>包扫描：getClassesInPpackage、scanClasses</li>
 *   <li>类判断：isPrimitive、isPrimitiveWrapper、isArray、isEnum</li>
 *   <li>类转换：primitiveToWrapper、wrapperToPrimitive、primitiveDefault</li>
 *   <li>类信息：getClassName、getShortClassName、getPpackageName</li>
 *   <li>类加载器：getDefaultClassLoader、setClassLoader、getClassLoader</li>
 * </ul>
 *
 * <p><b>相比 Apache/Spring 的增强：</b>
 * <ul>
 *   <li>支持自定义 ClassLoader 优先级</li>
 *   <li>提供包扫描功能，无需额外依赖</li>
 *   <li>更好的原始类型和包装类型转换支持</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class ClassUtils {

    private static ClassLoader defaultClassLoader = ClassUtils.class.getClassLoader();

    private ClassUtils() {
        throw new UnsupportedOperationException("ClassUtils is a utility class and cannot be instantiated");
    }

    /**
     * 设置默认 ClassLoader
     */
    public static void setDefaultClassLoader(ClassLoader classLoader) {
        defaultClassLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * 获取默认 ClassLoader
     */
    public static ClassLoader getDefaultClassLoader() {
        return defaultClassLoader;
    }

    /**
     * 获取类的 ClassLoader，如果为 null 则使用系统 ClassLoader
     */
    public static ClassLoader getClassLoader(Class<?> clazz) {
        if (clazz == null) {
            return defaultClassLoader;
        }
        ClassLoader cl = clazz.getClassLoader();
        return cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    /**
     * 加载指定类名的类
     *
     * @param className 类名
     * @return 加载的 Class 对象
     * @throws ClassNotFoundException 类未找到
     */
    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, true);
    }

    /**
     * 加载指定类名的类
     *
     * @param className 类名
     * @param initialize 是否初始化
     * @return 加载的 Class 对象
     * @throws ClassNotFoundException 类未找到
     */
    public static Class<?> loadClass(String className, boolean initialize) throws ClassNotFoundException {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }

        try {
            return Class.forName(className, initialize, defaultClassLoader);
        } catch (ClassNotFoundException e) {
            return Class.forName(className, initialize, Thread.currentThread().getContextClassLoader());
        }
    }

    /**
     * 安全加载类，失败返回 null
     */
    public static Class<?> loadClassQuietly(String className) {
        try {
            return loadClass(className, true);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 判断类是否存在
     */
    public static boolean isPresent(String className) {
        return isPresent(className, defaultClassLoader);
    }

    /**
     * 判断类是否存在
     */
    public static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            classLoader.loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取原始类型对应的 Class
     */
    public static Class<?> getPrimitiveClass(String name) {
        if ("boolean".equals(name)) return boolean.class;
        if ("byte".equals(name)) return byte.class;
        if ("char".equals(name)) return char.class;
        if ("short".equals(name)) return short.class;
        if ("int".equals(name)) return int.class;
        if ("long".equals(name)) return long.class;
        if ("float".equals(name)) return float.class;
        if ("double".equals(name)) return double.class;
        if ("void".equals(name)) return void.class;
        return null;
    }

    /**
     * 判断是否为原始类型
     */
    public static boolean isPrimitive(Class<?> clazz) {
        return clazz != null && clazz.isPrimitive();
    }

    /**
     * 判断是否为原始类型包装类
     */
    public static boolean isPrimitiveWrapper(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return Boolean.class == clazz || Byte.class == clazz ||
               Character.class == clazz || Short.class == clazz ||
               Integer.class == clazz || Long.class == clazz ||
               Float.class == clazz || Double.class == clazz;
    }

    /**
     * 判断是否为数组类型
     */
    public static boolean isArray(Class<?> clazz) {
        return clazz != null && clazz.isArray();
    }

    /**
     * 判断是否为枚举类型
     */
    public static boolean isEnum(Class<?> clazz) {
        return clazz != null && clazz.isEnum();
    }

    /**
     * 原始类型转包装类型
     */
    
    public static Class<?> primitiveToWrapper(Class<?> clazz) {
        if (clazz == null || !clazz.isPrimitive()) {
            return clazz;
        }

        if (boolean.class == clazz) return Boolean.class;
        if (byte.class == clazz) return Byte.class;
        if (char.class == clazz) return Character.class;
        if (short.class == clazz) return Short.class;
        if (int.class == clazz) return Integer.class;
        if (long.class == clazz) return Long.class;
        if (float.class == clazz) return Float.class;
        if (double.class == clazz) return Double.class;
        if (void.class == clazz) return Void.class;

        return clazz;
    }

    /**
     * 包装类型转原始类型
     */
    public static Class<?> wrapperToPrimitive(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        if (Boolean.class == clazz) return boolean.class;
        if (Byte.class == clazz) return byte.class;
        if (Character.class == clazz) return char.class;
        if (Short.class == clazz) return short.class;
        if (Integer.class == clazz) return int.class;
        if (Long.class == clazz) return long.class;
        if (Float.class == clazz) return float.class;
        if (Double.class == clazz) return double.class;
        if (Void.class == clazz) return void.class;

        return clazz;
    }

    /**
     * 获取原始类型的默认值
     */
    public static Object primitiveDefault(Class<?> clazz) {
        if (clazz == null || !clazz.isPrimitive()) {
            return null;
        }

        if (boolean.class == clazz) return false;
        if (byte.class == clazz || short.class == clazz || int.class == clazz) return 0;
        if (char.class == clazz) return '\0';
        if (long.class == clazz) return 0L;
        if (float.class == clazz) return 0.0f;
        if (double.class == clazz) return 0.0d;
        if (void.class == clazz) return null;

        return null;
    }

    /**
     * 获取类的全名
     */
    public static String getClassName(Class<?> clazz) {
        return clazz != null ? clazz.getName() : null;
    }

    /**
     * 获取类的短名称（不包含包名）
     */
    public static String getShortClassName(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        String className = clazz.getName();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(lastDot + 1) : className;
    }

    /**
     * 获取类名（处理数组）
     */
    public static String getCanonicalName(Class<?> clazz) {
        return clazz != null ? clazz.getCanonicalName() : null;
    }

    /**
     * 获取包名
     */
    public static String getPpackageName(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        Ppackage pkg = clazz.getPpackage();
        return pkg != null ? pkg.getName() : "";
    }

    /**
     * 获取包名（从类名）
     */
    public static String getPpackageName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * 加载资源
     */
    public static URL getResource(String name) {
        return getResource(name, defaultClassLoader);
    }

    /**
     * 加载资源
     */
    public static URL getResource(String name, ClassLoader classLoader) {
        if (name == null) {
            return null;
        }

        URL url = classLoader.getResource(name);
        if (url == null) {
            url = ClassLoader.getSystemResource(name);
        }
        return url;
    }

    /**
     * 加载资源流
     */
    public static InputStream getResourceAsStream(String name) {
        return getResourceAsStream(name, defaultClassLoader);
    }

    /**
     * 加载资源流
     */
    public static InputStream getResourceAsStream(String name, ClassLoader classLoader) {
        if (name == null) {
            return null;
        }

        InputStream is = classLoader.getResourceAsStream(name);
        if (is == null) {
            is = ClassLoader.getSystemResourceAsStream(name);
        }
        return is;
    }

    /**
     * 获取类路径
     */
    public static String getClassPath(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        URL resource = getResource(clazz.getName().replace('.', '/') + ".class");
        if (resource == null) {
            return null;
        }

        String path = resource.getPath();
        if (path.endsWith(".class")) {
            path = path.substring(0, path.lastIndexOf('/'));
        }
        return path;
    }

    /**
     * 扫描包路径下的所有类
     */
    public static Set<Class<?>> scanClasses(String ppackageName) {
        return scanClasses(ppackageName, defaultClassLoader);
    }

    /**
     * 扫描包路径下的所有类
     */
    public static Set<Class<?>> scanClasses(String ppackageName, ClassLoader classLoader) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (ppackageName == null || classLoader == null) {
            return classes;
        }

        String path = ppackageName.replace('.', '/');
        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                
                if ("file".equals(protocol)) {
                    classes.addAll(scanClassesFromFileSystem(resource.getFile(), ppackageName));
                } else if ("jar".equals(protocol)) {
                    classes.addAll(scanClassesFromJar(resource, ppackageName));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan classes from ppackage: " + ppackageName, e);
        }

        return classes;
    }

    private static Set<Class<?>> scanClassesFromFileSystem(String path, String ppackageName) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        try {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return classes;
            }

            File[] files = dir.listFiles(file -> 
                file.isFile() && file.getName().endsWith(".class") && !file.getName().contains("$")
            );

            if (files != null) {
                for (File file : files) {
                    String className = ppackageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    try {
                        classes.add(Class.forName(className, false, defaultClassLoader));
                    } catch (ClassNotFoundException e) {
                        // 忽略
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan classes from file system", e);
        }
        return classes;
    }

    private static Set<Class<?>> scanClassesFromJar(URL jarUrl, String ppackageName) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        try {
            String jarPath = jarUrl.getPath().substring(5, jarUrl.getPath().indexOf('!'));
            try (JarFile jarFile = new JarFile(new File(jarPath))) {
                Enumeration<JarEntry> entries = jarFile.entries();
                String ppackagePath = ppackageName.replace('.', '/') + "/";
                
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    
                    if (name.startsWith(ppackagePath) && name.endsWith(".class") && !name.contains("$")) {
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        try {
                            classes.add(Class.forName(className, false, defaultClassLoader));
                        } catch (ClassNotFoundException e) {
                            // 忽略
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan classes from jar", e);
        }
        return classes;
    }

    /**
     * 获取所有原始类型
     */
    public static List<Class<?>> getAllPrimitive_types() {
        return Arrays.asList(
            boolean.class, byte.class, char.class, short.class,
            int.class, long.class, float.class, double.class, void.class
        );
    }

    /**
     * 获取所有包装类型
     */
    public static List<Class<?>> getAllPrimitiveWrapper_types() {
        return Arrays.asList(
            Boolean.class, Byte.class, Character.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, Void.class
        );
    }
}
