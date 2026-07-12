package com.njydsz.pmis.common.safe.sensitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鏁忔劅鏁版嵁鑴辨晱 AOP 鎷︽埅鍣?
 *
 * <p>鍩轰簬 Spring {@link ResponseBodyAdvice} 瀹炵幇锛屽湪 Controller 鏂规硶杩斿洖鍊?
 * 鍐欏叆 HTTP 鍝嶅簲浣撲箣鍓嶏紝鑷姩瀵硅繑鍥炲€间腑鐨勬晱鎰熷瓧娈佃繘琛岃劚鏁忓鐞嗐€?
 *
 * <p><b>宸ヤ綔鍘熺悊锛?/b>
 * <ul>
 *   <li>鎷︽埅鎵€鏈夊甫鏈?{@link SensitiveData} 娉ㄨВ瀛楁鐨勬柟娉曡繑鍥炲€?/li>
 *   <li>鏀寔鍏ㄥ眬鑴辨晱瑙勫垯锛堥€氳繃瀛楁鍚嶅尮閰嶏級</li>
 *   <li>鍦ㄥ簭鍒楀寲鍓嶈皟鐢?{@link SensitiveDataProcessor} 杩涜鑴辨晱</li>
 *   <li>鏀寔閰嶇疆寮€鍏?{@code remi.safe.sensitive.enabled} 鎺у埗鏄惁鍚敤</li>
 *   <li>浣跨敤缂撳瓨鏈哄埗閬垮厤閲嶅妫€鏌ュ悓涓€涓被</li>
 * </ul>
 *
 * <p><b>浣跨敤绀轰緥锛?/b>
 * <pre>{@code
 * @RestController
 * public class UserController {
 *     @GetMapping("/user/{id}")
 *     public UserVO getUser(@PathVariable Long id) {
 *         // 杩斿洖鐨?UserVO 涓甫鏈?@SensitiveData 娉ㄨВ鐨勫瓧娈典細鑷姩鑴辨晱
 *         return userService.findById(id);
 *     }
 * }
 *
 * public class UserVO {
 *     @SensitiveData(SensitiveType.PHONE)
 *     private String phone;
 *
 *     @SensitiveData(SensitiveType.NAME)
 *     private String name;
 * }
 * }</pre>
 *
 * <p><b>閰嶇疆寮€鍏筹細</b>
 * <pre>{@code
 * remi:
 *   safe:
 *     sensitive:
 *       enabled: true  # 榛樿鍚敤
 *       max-depth: 10  # 鏈€澶ч€掑綊娣卞害
 *       # 鍏ㄥ眬鑴辨晱瑙勫垯锛堝彲閫夛級
 *       global-rules:
 *         - field-name: phone
 *           type: PHONE
 *         - field-name: idCard
 *           type: ID_CARD
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see SensitiveData
 * @see SensitiveDataProcessor
 * @see SensitiveDataConfiguration
 */
@RestControllerAdvice
public class SensitiveDataAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataAdvice.class);

    private final SensitiveDataConfiguration configuration;

    /**
     * 缂撳瓨绫荤殑鏁忔劅瀛楁妫€鏌ョ粨鏋滐紝閬垮厤閲嶅鍙嶅皠妫€鏌?
     * Key: Class瀵硅薄锛孷alue: 鏄惁鍖呭惈鏁忔劅瀛楁
     */
    private final Map<Class<?>, Boolean> sensitiveClassCache = new ConcurrentHashMap<>();

    public SensitiveDataAdvice(SensitiveDataConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 濡傛灉鏈惎鐢ㄨ劚鏁忥紝鐩存帴璺宠繃
        if (!configuration.isEnabled()) {
            return false;
        }

        // 妫€鏌ヨ繑鍥炵被鍨嬪強鍏跺瓧娈垫槸鍚﹀寘鍚?@SensitiveData 娉ㄨВ鎴栧尮閰嶅叏灞€瑙勫垯
        Class<?> returnTypeClass = returnType.getParameterType();
        return containsSensitiveAnnotation(returnTypeClass, returnType);
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body == null) {
            return body;
        }

        try {
            log.debug("寮€濮嬪杩斿洖鍊艰繘琛屾晱鎰熸暟鎹劚鏁? {}", returnType.getParameterType().getName());
            return SensitiveDataProcessor.process(body, configuration.getMaxDepth());
        } catch (Exception e) {
            // 鑴辨晱澶辫触杩斿洖绌哄璞★紝闃叉鍘熷鏈劚鏁忔暟鎹硠闇?
            log.error("鏁忔劅鏁版嵁鑴辨晱澶勭悊澶辫触锛岃繑鍥炵┖瀵硅薄浠ラ伩鍏嶆暟鎹硠闇? {}", e.getMessage(), e);
            return createEmptyObject(body.getClass());
        }
    }

    /**
     * 鍒涘缓鎸囧畾绫诲瀷鐨勭┖瀵硅薄锛岀敤浜庤劚鏁忓け璐ユ椂杩斿洖
     * 閬垮厤鍘熷鏁版嵁娉勯湶
     */
    private Object createEmptyObject(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            log.error("鍒涘缓绌哄璞″け璐? {}", clazz.getName(), ex);
            return null;
        }
    }

    /**
     * 閫掑綊妫€鏌ョ被鍙婂叾瀛楁鏄惁鍖呭惈 {@link SensitiveData} 娉ㄨВ鎴栧尮閰嶅叏灞€瑙勫垯
     *
     * @param clazz 寰呮鏌ョ殑绫?
     * @param methodParameter 鏂规硶鍙傛暟锛堢敤浜庤幏鍙栨硾鍨嬩俊鎭級
     * @return 鏄惁鍖呭惈鏁忔劅鏁版嵁娉ㄨВ
     */
    private boolean containsSensitiveAnnotation(Class<?> clazz, MethodParameter methodParameter) {
        // 浣跨敤缂撳瓨閬垮厤閲嶅妫€鏌?
        return sensitiveClassCache.computeIfAbsent(clazz, c -> 
            doCheckSensitiveAnnotation(c, methodParameter, 0));
    }

    /**
     * 瀹為檯鎵ц鏁忔劅娉ㄨВ妫€鏌?
     */
    private boolean doCheckSensitiveAnnotation(Class<?> clazz, MethodParameter methodParameter, int depth) {
        if (clazz == null || clazz == Object.class || depth > configuration.getMaxDepth()) {
            return false;
        }

        // 妫€鏌ュ綋鍓嶇被鐨勬墍鏈夊瓧娈?
        for (Field field : clazz.getDeclaredFields()) {
            // 妫€鏌ュ瓧娈垫槸鍚︽湁 @SensitiveData 娉ㄨВ
            if (field.isAnnotationPresent(SensitiveData.class)) {
                return true;
            }

            // 妫€鏌ュ瓧娈垫槸鍚﹀尮閰嶅叏灞€鑴辨晱瑙勫垯
            if (matchesGlobalRule(field.getName())) {
                return true;
            }

            // 妫€鏌ュ祵濂楀璞＄殑瀛楁
            Class<?> fieldType = field.getType();
            
            // 澶勭悊闆嗗悎绫诲瀷锛屽皾璇曡幏鍙栨硾鍨嬪弬鏁?
            if (Collection.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        Class<?> elementType = (Class<?>) typeArgs[0];
                        if (!isSimpleType(elementType) && 
                            doCheckSensitiveAnnotation(elementType, methodParameter, depth + 1)) {
                            return true;
                        }
                    }
                }
                // 鏃犳硶鑾峰彇娉涘瀷淇℃伅鏃讹紝淇濆畧杩斿洖 true
                return true;
            }
            
            // 澶勭悊 Map 绫诲瀷锛屽皾璇曡幏鍙栧€肩殑娉涘瀷鍙傛暟
            if (Map.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArgs.length > 1 && typeArgs[1] instanceof Class) {
                        Class<?> valueType = (Class<?>) typeArgs[1];
                        if (!isSimpleType(valueType) && 
                            doCheckSensitiveAnnotation(valueType, methodParameter, depth + 1)) {
                            return true;
                        }
                    }
                }
                // 鏃犳硶鑾峰彇娉涘瀷淇℃伅鏃讹紝淇濆畧杩斿洖 true
                return true;
            }
            
            // 澶勭悊鏅€氬璞＄被鍨?
            if (!isSimpleType(fieldType)) {
                if (doCheckSensitiveAnnotation(fieldType, methodParameter, depth + 1)) {
                    return true;
                }
            }
        }

        // 妫€鏌ョ埗绫?
        return doCheckSensitiveAnnotation(clazz.getSuperclass(), methodParameter, depth + 1);
    }

    /**
     * 妫€鏌ュ瓧娈靛悕鏄惁鍖归厤鍏ㄥ眬鑴辨晱瑙勫垯
     *
     * @param fieldName 瀛楁鍚?
     * @return 鏄惁鍖归厤鍏ㄥ眬瑙勫垯
     */
    private boolean matchesGlobalRule(String fieldName) {
        if (configuration.getGlobalRules() == null || configuration.getGlobalRules().isEmpty()) {
            return false;
        }

        return configuration.getGlobalRules().stream()
            .filter(SensitiveDataConfiguration.GlobalDesensitizeRule::isEnabled)
            .anyMatch(rule -> matchesFieldName(fieldName, rule.getFieldName()));
    }

    /**
     * 鍖归厤瀛楁鍚嶏紙鏀寔閫氶厤绗︼級
     *
     * @param fieldName 瀹為檯瀛楁鍚?
     * @param pattern 鍖归厤妯″紡锛堟敮鎸?* 閫氶厤绗︼級
     * @return 鏄惁鍖归厤
     */
    private boolean matchesFieldName(String fieldName, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // 绮剧‘鍖归厤
        if (!pattern.contains("*")) {
            return fieldName.equals(pattern);
        }

        // 閫氶厤绗﹀尮閰?
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return fieldName.matches(regex);
    }

    /**
     * 鍒ゆ柇鏄惁涓虹畝鍗曠被鍨嬶紙鏃犻渶妫€鏌ユ敞瑙ｏ級
     *
     * @param clazz 绫诲瀷
     * @return 鏄惁涓虹畝鍗曠被鍨?
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || Date.class.isAssignableFrom(clazz)
                || java.time.temporal.Temporal.class.isAssignableFrom(clazz);
    }
}
