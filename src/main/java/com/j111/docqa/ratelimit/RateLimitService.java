package com.j111.docqa.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 令牌桶限流（Redis + Lua）。
 * <p>
 * 为什么用 Lua：令牌桶要"读令牌数 → 计算补充 → 判断 → 扣减 → 写回"，
 * 这几步必须是原子的。如果在 Java 里分几次命令执行，高并发下两个线程会读到同一个令牌数，
 * 结果就是限流形同虚设（这是限流实现里最常见的 bug）。
 * <p>
 * 为什么用令牌桶而不是固定窗口计数：固定窗口在窗口交界处会放过 2 倍流量
 * （第 59 秒来 10 个，第 61 秒又来 10 个），令牌桶以恒定速率补充，流量更平滑。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /**
     * KEYS[1] 桶的 key
     * ARGV[1] 容量, ARGV[2] 每秒补充速率, ARGV[3] 当前毫秒时间戳, ARGV[4] key 过期秒数
     * 返回 1 = 放行，0 = 拒绝
     */
    private static final String LUA = """
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end

            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * rate)
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end

            redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', KEYS[1], ttl)
            return allowed
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /**
     * @return true = 放行
     */
    public boolean tryAcquire(String bucketKey, int capacity, int perSeconds) {
        double ratePerSecond = (double) capacity / perSeconds;
        // key 过期时间给窗口的 2 倍，闲置的桶自动清理，不会无限涨内存
        int ttl = Math.max(perSeconds * 2, 60);
        try {
            Long allowed = redisTemplate.execute(
                    script,
                    List.of("docqa:rate:" + bucketKey),
                    String.valueOf(capacity),
                    String.valueOf(ratePerSecond),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(ttl));
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            // fail-open：Redis 挂了不能把整个服务也弄挂，宁可暂时不限流
            log.warn("限流器不可用，本次放行: {}", e.getMessage());
            return true;
        }
    }
}
