package cn.pandora.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "数据冲突"),

    INTERNAL_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // 业务错误码 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "用户密码错误"),
    ;

    private final int code;
    private final String message;
}
