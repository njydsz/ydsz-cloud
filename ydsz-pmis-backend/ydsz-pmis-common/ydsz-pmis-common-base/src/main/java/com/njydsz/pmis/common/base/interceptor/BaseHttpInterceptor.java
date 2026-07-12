package com.njydsz.pmis.common.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 璇锋眰缁撴潫娓呯悊鎷︽埅鍣紙Web/App 鍏变韩锛?
 *
 * <p>浣滀负榛樿鐨?{@link HandlerInterceptor} 瀹炵幇锛屽畾涔夊湪鎷︽埅鍣ㄩ摼鐨勬渶鏈锛?
 * 鐢ㄤ簬鍦ㄨ姹傚畬鎴愬悗鎵ц娓呯悊鍔ㄤ綔銆?
 *
 * <p><b>鑱岃矗璇存槑锛?/b>
 * <ul>
 *   <li>{@link RequestHolder#remove()} 绛?ThreadLocal 娓呯悊鐢?
 *       {@code BaseAuthFilter.doFilterInternal()} 鐨?finally 鍧楃粺涓€璐熻矗</li>
 *   <li>姝ょ被浠呬綔涓哄崰浣嶆嫤鎴櫒锛屽彲鐢变笟鍔℃柟閫氳繃瑕嗙洊
 *       {@link #afterCompletion(HttpServletRequest, HttpServletResponse, Object, Exception)}
 *       鎵╁睍鑷畾涔夋竻鐞嗛€昏緫</li>
 * </ul>
 *
 * <p>鎷︽埅鍣ㄦ墽琛岄『搴忓弬鑰?{@code docs/BASE_INTERCEPTOR_ORDER.md}銆?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public class BaseHttpInterceptor implements HandlerInterceptor {

    /**
     * 璇锋眰瀹屾垚鍚庡洖璋?
     *
     * <p>榛樿绌哄疄鐜帮紝涓氬姟鏂瑰彲閫氳繃缁ф壙姝ゆ嫤鎴櫒骞惰鐩栨鏂规硶瀹炵幇鑷畾涔夋竻鐞嗐€?
     *
     * @param request  HTTP 璇锋眰
     * @param response HTTP 鍝嶅簲
     * @param handler  澶勭悊鍣?
     * @param ex       澶勭悊杩囩▼涓姏鍑虹殑寮傚父锛堟棤寮傚父鏃朵负 null锛?
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        // RequestHolder.remove() 鐢?BaseAuthFilter.doFilterInternal() 鐨?finally 鍧楄礋璐ｆ竻鐞嗭紝姝ゅ涓嶅啀閲嶅璋冪敤
    }
}
