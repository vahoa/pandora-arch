package cn.pandora.common.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean 复制工具类（基于 Hutool）
 */
public final class BeanCopyUtils {

    private BeanCopyUtils() {
    }

    public static <T> T copy(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        return BeanUtil.copyProperties(source, targetClass);
    }

    public static void copy(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        BeanUtil.copyProperties(source, target);
    }

    /**
     * 复制属性，忽略 null 值
     */
    public static void copyIgnoreNull(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        BeanUtil.copyProperties(source, target, CopyOptions.create().ignoreNullValue());
    }

    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null || sourceList.isEmpty()) {
            return Collections.emptyList();
        }
        return sourceList.stream()
                .map(source -> copy(source, targetClass))
                .collect(Collectors.toList());
    }
}
