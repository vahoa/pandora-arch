package cn.pandora.infrastructure.ai;

import cn.pandora.application.ai.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 基于 Spring AI 的对话服务实现
 */
@Slf4j
public class AiChatService implements AiService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String userMessage) {
        log.debug("AI对话请求: {}", userMessage);
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    @Override
    public String chat(String userMessage, String systemPrompt) {
        log.debug("AI对话请求（含系统提示）: system={}, user={}", systemPrompt, userMessage);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    @Override
    public String summarize(String text) {
        String prompt = "请对以下文本进行简要摘要，用中文回答：\n\n" + text;
        return chat(prompt, "你是一个专业的文本摘要助手。");
    }

    @Override
    public String translate(String text, String targetLanguage) {
        String prompt = "请将以下文本翻译为" + targetLanguage + "：\n\n" + text;
        return chat(prompt, "你是一个专业的翻译助手，只输出翻译结果，不要添加解释。");
    }
}
