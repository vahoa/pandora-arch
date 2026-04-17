package cn.pandora.common.util;

import cn.pandora.common.exception.BusinessException;
import cn.pandora.common.result.ResultCode;

import java.util.Collection;
import java.util.Objects;

/**
 * 业务断言工具 —— 校验不通过时直接抛出 BusinessException
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    public static void notNull(Object obj, ResultCode resultCode) {
        if (obj == null) {
            throw new BusinessException(resultCode);
        }
    }

    public static void notNull(Object obj, ResultCode resultCode, String message) {
        if (obj == null) {
            throw new BusinessException(resultCode, message);
        }
    }

    public static void notBlank(String str, ResultCode resultCode) {
        if (str == null || str.isBlank()) {
            throw new BusinessException(resultCode);
        }
    }

    public static void notBlank(String str, ResultCode resultCode, String message) {
        if (str == null || str.isBlank()) {
            throw new BusinessException(resultCode, message);
        }
    }

    public static void notEmpty(Collection<?> collection, ResultCode resultCode) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(resultCode);
        }
    }

    public static void isTrue(boolean expression, ResultCode resultCode) {
        if (!expression) {
            throw new BusinessException(resultCode);
        }
    }

    public static void isTrue(boolean expression, ResultCode resultCode, String message) {
        if (!expression) {
            throw new BusinessException(resultCode, message);
        }
    }

    public static void isFalse(boolean expression, ResultCode resultCode) {
        if (expression) {
            throw new BusinessException(resultCode);
        }
    }

    public static void equals(Object a, Object b, ResultCode resultCode) {
        if (!Objects.equals(a, b)) {
            throw new BusinessException(resultCode);
        }
    }
}
