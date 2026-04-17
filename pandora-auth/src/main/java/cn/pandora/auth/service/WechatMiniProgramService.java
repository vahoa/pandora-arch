package cn.pandora.auth.service;

import cn.pandora.auth.config.AuthProperties;
import cn.pandora.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信小程序登录服务 —— 通过 code2Session 接口换取 openid/session_key
 */
@Slf4j
@Service
public class WechatMiniProgramService {

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final AuthProperties authProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WechatMiniProgramService(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * 使用微信小程序 code 换取用户 openid
     */
    public MiniProgramSession code2Session(String code) {
        AuthProperties.WechatMini config = authProperties.getWechatMini();
        if (config.getAppId() == null || config.getAppSecret() == null) {
            throw new BusinessException("微信小程序配置缺失");
        }

        String url = String.format(CODE2SESSION_URL,
                config.getAppId(), config.getAppSecret(), code);

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                throw new BusinessException("微信小程序登录失败: " + json.get("errmsg").asText());
            }

            MiniProgramSession session = new MiniProgramSession();
            session.setOpenId(json.get("openid").asText());
            session.setSessionKey(json.get("session_key").asText());
            if (json.has("unionid")) {
                session.setUnionId(json.get("unionid").asText());
            }
            return session;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信小程序 code2Session 调用失败", e);
            throw new BusinessException("微信小程序登录请求失败");
        }
    }

    @Data
    public static class MiniProgramSession {
        private String openId;
        private String sessionKey;
        private String unionId;
    }
}
