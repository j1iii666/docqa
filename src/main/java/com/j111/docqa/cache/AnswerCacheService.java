package com.j111.docqa.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

/**
 * 问答结果缓存。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>key 里带 userId：不同用户上传的文档不一样，缓存绝不能跨用户共享（否则会串答案）</li>
 *   <li>key 里带 topK：取了 3 段和取了 5 段生成的答案可能不同</li>
 *   <li>问题做归一化（去空格、转小写）+ MD5：避免 key 里出现超长中文和特殊字符</li>
 *   <li>Redis 任何异常都只打日志、返回空：缓存是为了快，挂了不能把业务搞挂</li>
 * </ul>
 */
@Service
public class AnswerCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnswerCacheService.class);
    private static final String KEY_PREFIX = "docqa:answer:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final boolean enabled;

    public AnswerCacheService(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              @Value("${docqa.cache.answer-ttl-seconds:3600}") long ttlSeconds,
                              @Value("${docqa.cache.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
        this.enabled = enabled;
    }

    /** 缓存的答案体：正文 + 来源列表（来源也要一起缓存，否则命中缓存时前端拿不到引用） */
    public record CachedAnswer(String answer, String sourcesJson) {
    }

    public Optional<CachedAnswer> get(Long userId, String query, int topK) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String raw = redisTemplate.opsForValue().get(buildKey(userId, query, topK));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, CachedAnswer.class));
        } catch (Exception e) {
            // 超时、连接断了、数据格式变了，都当作没命中
            log.warn("读缓存失败，降级为直接查询: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long userId, String query, int topK, CachedAnswer value) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    buildKey(userId, query, topK),
                    objectMapper.writeValueAsString(value),
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写缓存失败，本次结果不缓存: {}", e.getMessage());
        }
    }

    /** 某个用户的文档变了（上传/删除）时清掉他的问答缓存 */
    public void evictByUser(Long userId) {
        if (!enabled) {
            return;
        }
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理缓存失败: {}", e.getMessage());
        }
    }

    private String buildKey(Long userId, String query, int topK) {
        String normalized = query == null ? "" : query.strip().toLowerCase();
        return KEY_PREFIX + userId + ":" + topK + ":" + md5(normalized);
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
