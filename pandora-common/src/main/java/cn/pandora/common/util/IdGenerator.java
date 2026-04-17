package cn.pandora.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 分布式 ID 生成器（基于 Hutool 雪花算法）
 */
public final class IdGenerator {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private IdGenerator() {
    }

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    public static String uuid() {
        return IdUtil.fastSimpleUUID();
    }

    public static String objectId() {
        return IdUtil.objectId();
    }
}
