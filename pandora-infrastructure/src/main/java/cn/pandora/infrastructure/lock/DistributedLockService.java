package cn.pandora.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务（Redisson） —— 业务可直接注入使用
 * <p>
 * 推荐：{@link #runWithLock(String, long, long, Runnable)} —— 可重入 + 自动续期 + 异常释放
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 以 supplier 的返回值作为执行结果，加锁并自动释放
     */
    public <T> T callWithLock(String key, long waitSec, long leaseSec, Supplier<T> task) {
        RLock lock = redissonClient.getLock("lock:" + key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSec, leaseSec, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("获取分布式锁失败: " + key);
            }
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取锁被中断: " + key, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void runWithLock(String key, long waitSec, long leaseSec, Runnable task) {
        callWithLock(key, waitSec, leaseSec, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 读写锁 - 写锁
     */
    public <T> T callWithWriteLock(String key, long waitSec, long leaseSec, Supplier<T> task) {
        RLock lock = redissonClient.getReadWriteLock("rwlock:" + key).writeLock();
        return doWithLock(lock, key, waitSec, leaseSec, task);
    }

    /**
     * 读写锁 - 读锁
     */
    public <T> T callWithReadLock(String key, long waitSec, long leaseSec, Supplier<T> task) {
        RLock lock = redissonClient.getReadWriteLock("rwlock:" + key).readLock();
        return doWithLock(lock, key, waitSec, leaseSec, task);
    }

    private <T> T doWithLock(RLock lock, String key, long waitSec, long leaseSec, Supplier<T> task) {
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSec, leaseSec, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalStateException("获取锁失败: " + key);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取锁被中断: " + key, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
