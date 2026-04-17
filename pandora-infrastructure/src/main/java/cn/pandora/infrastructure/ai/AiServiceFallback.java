package cn.pandora.infrastructure.ai;

import cn.pandora.application.ai.AiService;

/**
 * AI 服务降级实现 —— 未配置 AI 提供者时使用
 */
public class AiServiceFallback implements AiService {

    private static final String FALLBACK_MSG = "AI 服务未配置。请在 application.yml 中配置 spring.ai.openai.api-key 以启用 AI 功能。";

    @Override
    public String chat(String userMessage) {
        return FALLBACK_MSG;
    }

    @Override
    public String chat(String userMessage, String systemPrompt) {
        return FALLBACK_MSG;
    }

    @Override
    public String summarize(String text) {
        return FALLBACK_MSG;
    }

    @Override
    public String translate(String text, String targetLanguage) {
        return FALLBACK_MSG;
    }
}
