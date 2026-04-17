package cn.pandora.application.ai;

/**
 * AI 对话服务接口（应用层定义，基础设施层实现）
 */
public interface AiService {

    /**
     * 单轮对话
     */
    String chat(String userMessage);

    /**
     * 带系统提示的对话
     */
    String chat(String userMessage, String systemPrompt);

    /**
     * 文本摘要
     */
    String summarize(String text);

    /**
     * 文本翻译
     */
    String translate(String text, String targetLanguage);
}
