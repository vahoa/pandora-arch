package cn.pandora.interfaces.rest;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 Token 端点 —— 供 Swagger Authorize 弹窗直接调用
 * <p>
 * Swagger UI 的 OAuth2 Password Flow 会自动 POST 此端点，
 * 传入 grant_type=password&username=xxx&password=xxx，
 * 返回标准 OAuth2 Token Response。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Hidden
public class AuthProxyController {

    @Value("${auth.server-url:http://localhost:9100}")
    private String authServerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam("username") String username,
            @RequestParam("password") String password) {

        try {
            String url = authServerUrl + "/auth/login";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "loginType", "USERNAME_PASSWORD",
                    "username", username,
                    "password", password
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().get("data") != null) {

                Map<String, Object> authData = (Map<String, Object>) response.getBody().get("data");

                Map<String, Object> tokenResponse = new HashMap<>();
                tokenResponse.put("access_token", authData.get("accessToken"));
                tokenResponse.put("token_type", "Bearer");
                tokenResponse.put("expires_in", authData.get("expiresIn"));
                tokenResponse.put("refresh_token", authData.get("refreshToken"));

                return ResponseEntity.ok(tokenResponse);
            }

            return ResponseEntity.status(401).body(Map.of("error", "invalid_grant", "error_description", "用户名或密码错误"));
        } catch (Exception e) {
            log.error("调用认证服务失败", e);
            return ResponseEntity.status(503).body(Map.of(
                    "error", "server_error",
                    "error_description", "认证服务不可用，请确保 Auth 服务已启动在 " + authServerUrl));
        }
    }
}
