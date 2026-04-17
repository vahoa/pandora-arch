package cn.pandora.common.exception;

import cn.pandora.common.result.ResultCode;
import lombok.Getter;

/**
 * 系统异常 —— 由系统内部非预期错误引发
 */
@Getter
public class SystemException extends RuntimeException {

    private final int code;

    public SystemException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }

    public SystemException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
