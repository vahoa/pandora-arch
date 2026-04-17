package cn.pandora.common.exception;

import cn.pandora.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常 —— 由业务规则校验不通过时抛出
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 便捷构造 —— 直接传入错误消息（默认 code 500）
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
