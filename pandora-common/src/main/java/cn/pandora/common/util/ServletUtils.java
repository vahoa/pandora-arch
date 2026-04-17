package cn.pandora.common.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Servlet 请求工具类
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    public static Optional<HttpServletRequest> getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(attrs).map(ServletRequestAttributes::getRequest);
    }

    public static Optional<HttpServletResponse> getResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Optional.ofNullable(attrs).map(ServletRequestAttributes::getResponse);
    }

    public static String getClientIp() {
        return getRequest().map(request -> {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }).orElse("unknown");
    }

    public static String getUserAgent() {
        return getRequest()
                .map(request -> request.getHeader("User-Agent"))
                .orElse("");
    }

    public static String getHeader(String name) {
        return getRequest()
                .map(request -> request.getHeader(name))
                .orElse(null);
    }
}
