package com.njydsz.pmis.common.safe.xss;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * OWASP-based XSS cleaner implementation
 *
 * <p>Replaces the custom HTMLFilter with OWASP Java HTML Sanitizer for better security and maintainability.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Industry-standard XSS protection</li>
 *   <li>Configurable sanitization policies</li>
 *   <li>Better performance and security</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class OwaspXssCleaner {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES);

    /**
     * Clean HTML content to prevent XSS attacks
     *
     * @param dirtyHtml untrusted HTML content
     * @return sanitized HTML content
     */
    public static String clean(String dirtyHtml) {
        if (dirtyHtml == null || dirtyHtml.isEmpty()) {
            return dirtyHtml;
        }
        return POLICY.sanitize(dirtyHtml);
    }

    /**
     * Clean JSON string values to prevent XSS attacks
     *
     * @param jsonString JSON string potentially containing XSS
     * @return sanitized JSON string
     */
    public static String cleanJsonValue(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }
        // For JSON, we sanitize the entire string
        return POLICY.sanitize(jsonString);
    }

    /**
     * Check if content contains potential XSS attacks
     *
     * @param content content to check
     * @return true if XSS detected, false otherwise
     */
    public static boolean containsXSS(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String sanitized = clean(content);
        return !content.equals(sanitized);
    }
}
