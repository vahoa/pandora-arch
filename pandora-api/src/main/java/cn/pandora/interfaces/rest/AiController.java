package cn.pandora.interfaces.rest;

import cn.pandora.application.ai.AiService;
import cn.pandora.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话 REST 控制器
 */
@Tag(name = "AI 对话", description = "基于 Spring AI 的智能对话接口")
@RestController
@RequestMapping("/api/ai")
@Validated
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "AI 对话", description = "发送消息进行 AI 对话")
    @PostMapping("/chat")
    public Result<String> chat(@NotBlank @RequestParam String message) {
        String reply = aiService.chat(message);
        return Result.success(reply);
    }

    @Operation(summary = "带系统提示的 AI 对话")
    @PostMapping("/chat/with-system")
    public Result<String> chatWithSystem(
            @NotBlank @RequestParam String message,
            @NotBlank @RequestParam String systemPrompt) {
        String reply = aiService.chat(message, systemPrompt);
        return Result.success(reply);
    }

    @Operation(summary = "文本摘要", description = "使用 AI 生成文本摘要")
    @PostMapping("/summarize")
    public Result<String> summarize(@NotBlank @RequestBody String text) {
        String summary = aiService.summarize(text);
        return Result.success(summary);
    }

    @Operation(summary = "文本翻译", description = "使用 AI 翻译文本")
    @PostMapping("/translate")
    public Result<String> translate(
            @NotBlank @RequestParam String text,
            @RequestParam(defaultValue = "英文") String targetLanguage) {
        String result = aiService.translate(text, targetLanguage);
        return Result.success(result);
    }
}
