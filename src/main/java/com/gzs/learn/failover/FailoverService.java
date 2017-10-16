package com.gzs.learn.failover;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

@Slf4j
@Component
public class FailoverService {
    // 间隔多久后重试,以分钟为单位
    private static final int[] DEFAULT_FAILOVER_SEQ = {1, 5, 10, 15, 30, 60, 60 * 3, 60 * 12};
    private static final String ZSET_NAME = "-zset-";
    private static final String HASH_NAME = "-hash-meta";
    private static final AtomicInteger key = new AtomicInteger(0);
    // 数据是否初始化
    private boolean isInit = false;
    // worker pool
    private ExecutorService pool = null;
    // timeunit
    private TimeUnit timeUnit = null;
    private final ReentrantLock mainLock = new ReentrantLock();
    private int[] seq = null;
    private final ConcurrentHashMap<Integer, FailoverHandler> registers = new ConcurrentHashMap<>();
    @Autowired
    private JedisSentinelPool jedisPool;
    @Value("${redis.db}")
    private int redisDB;
    // 重试次数序列,以逗号分隔
    @Value("${failover_seq}")
    private String failoverSeq;
    // zset前缀
    @Value("${failover_set_prefix}")
    private String failoverSetPrefix;
    @Value("${failover_work_count}")
    private String failoverWorkCount;

    public Object doFailOver(FailoverHandler handler, Object param) {
        if (StringUtils.isBlank(failoverSetPrefix)) {
            throw new IllegalArgumentException("failover set prefix must config");
        }
        // 初始化seq
        this.initConfig();
        // 注册处理器
        final int bizKey = this.registerHandler(handler);

        try {
            // 无异常直接返回
            return handler.handle(param);
        } catch (final Throwable e) {
            try {
                // 第一次失败立即发起一次重试
                return handler.handle(param);
            } catch (final Throwable e1) {
                final String firstSetName = this.createZsetName(bizKey, 0);
                final long firstSetScore = System.currentTimeMillis() + timeUnit.toMillis(seq[0]);
                // 丢入重试队列
                try (Jedis jedis = jedisPool.getResource()) {
                    final FailoverData data = new FailoverData(bizKey, param);
                    final String members = JSON.toJSONString(data);
                    jedis.select(redisDB);
                    jedis.zadd(firstSetName, firstSetScore, members);

                    return null;
                }
            }
        }
    }

    public void doRetry() {
        this.initConfig();
        final int currentBizKeyNum = key.get();
        // 业务类型数量
        for (int i = 0; i < currentBizKeyNum; i++) {
            final int bizKey = i;
            pool.submit(() -> this.doSingleBiz(bizKey, seq));
        }
    }

    // 处理单个业务类型的数据
    private void doSingleBiz(int bizKey, int[] seq) {
        final long score = System.currentTimeMillis();
        // 重试队列
        for (int i = 0; i < seq.length; i++) {
            this.handleSingleZset(bizKey, i, score, false);
        }
        // 处理最后一条队列内容
        final int last = seq.length;
        this.handleSingleZset(bizKey, last, score, true);
    }

    /**
     * 处理单个zset队列数据
     *
     * @param bizKey
     * @param index
     * @param score
     * @param isLast
     */
    private void handleSingleZset(int bizKey, int index, final long score, boolean isLast) {
        final FailoverHandler handler = registers.get(bizKey);
        final int nextIndex = index + 1;
        final String current = this.createZsetName(bizKey, index);
        final String next = this.createZsetName(bizKey, nextIndex);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.select(redisDB);
            final Set<String> dataSet = jedis.zrangeByScore(current, 0, score);
            jedis.zremrangeByScore(current, 0, score);
            for (final String data : dataSet) {
                final FailoverData failoverData = JSON.parseObject(data, FailoverData.class);
                final Object obj = JSON.parseObject(failoverData.getJson(), failoverData.getCls());
                try {
                    handler.handle(obj);
                    if (log.isDebugEnabled()) {
                        log.info("zset:{},data:{},retry success until times:{}", current, data,
                                index);
                    }
                } catch (final Throwable e) {
                    if (isLast) {
                        handler.onFail(obj);
                    } else {
                        final long nextScore = score + timeUnit.toMillis(seq[index]);
                        jedis.zadd(next, nextScore, data);
                    }
                }
            }
        }
    }

    private void initConfig() {
        final ReentrantLock lock = mainLock;
        try {
            lock.lock();
            if (!isInit) {
                try {
                    final int workCount = Integer.parseInt(failoverWorkCount);
                    pool = Executors.newFixedThreadPool(workCount);
                } catch (final Exception e) {
                    // default work thread num
                    pool = Executors.newFixedThreadPool(10);
                }
                if (StringUtils.isNotBlank(failoverSeq)) {
                    final String[] strs = failoverSeq.split(",");
                    final String type = strs[0];
                    // 类型
                    if (type.equalsIgnoreCase("ms")) {
                        timeUnit = TimeUnit.MILLISECONDS;
                    } else if (type.equalsIgnoreCase("s")) {
                        timeUnit = TimeUnit.SECONDS;
                    } else if (type.equals("m")) {
                        timeUnit = TimeUnit.MINUTES;
                    } else if (type.equalsIgnoreCase("h")) {
                        timeUnit = TimeUnit.HOURS;
                    }
                    seq = new int[strs.length - 1];
                    for (int i = 0; i < seq.length; i++) {
                        // due to strs[0] is type
                        seq[i] = Integer.parseInt(strs[i + 1].trim());
                    }
                } else {
                    // default settings
                    timeUnit = TimeUnit.MINUTES;
                    seq = DEFAULT_FAILOVER_SEQ;
                }
                isInit = true;
            }
        } finally {
            lock.unlock();
        }
    }

    private int registerHandler(FailoverHandler handler) {
        int bizKey = -1;
        if (!registers.containsValue(handler)) {
            bizKey = key.getAndIncrement();
            registers.put(bizKey, handler);
            // 向meta中写入信息
            final String hashName = failoverSetPrefix + HASH_NAME;
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.select(redisDB);
                for (final Entry<Integer, FailoverHandler> entry : registers.entrySet()) {
                    String className = entry.getValue().getClass().getName();
                    // 简单处理掉代理类
                    if (className.indexOf('$') > 0) {
                        className = className.substring(0, className.indexOf('$'));
                    }
                    jedis.hset(hashName, entry.getKey() + "", className);
                }
            }
        } else {
            for (final Entry<Integer, FailoverHandler> entry : registers.entrySet()) {
                if (entry.getValue() == handler) {
                    bizKey = entry.getKey();
                    break;
                }
            }
        }
        return bizKey;
    }

    private String createZsetName(final int bizKey, int setIndex) {
        return failoverSetPrefix + "-" + bizKey + ZSET_NAME + setIndex;
    }

    // 初始化handler
    public void initHandlers() {
        final ReentrantLock lock = mainLock;
        try {
            lock.lock();
            final String hashName = failoverSetPrefix + HASH_NAME;
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.select(redisDB);
                final Map<String, String> clsMap = jedis.hgetAll(hashName);

                for (final Entry<String, String> entry : clsMap.entrySet()) {
                    String className = entry.getValue().getClass().getName();
                    // 简单处理掉代理类
                    if (className.indexOf('$') > 0) {
                        className = className.substring(0, className.indexOf('$'));
                    }
                    try {
                        final Class<?> cls = Class.forName(className);
                        final FailoverHandler handler =
                                (FailoverHandler) ApplicationContextHolder.getBean(cls);
                        if (handler == null || registers.containsValue(handler)) {
                            continue;
                        }

                        final int bizKey = key.getAndIncrement();
                        registers.put(bizKey, handler);
                    } catch (final ClassNotFoundException e) {
                        log.warn("can not find handler with class:{}", className);
                        continue;
                    } catch (final Exception e) {
                        log.error("get handler {} failed due exception", className, e);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // 关闭时写入handler信息(线上服务使用kill -9 pid方式)
    /*
     * public void dumpHandlers() { final ReentrantLock lock = mainLock; try { lock.lock(); final
     * String hashName = failoverSetPrefix + HASH_NAME; final Jedis jedis = jedisPool.getResource();
     * jedis.select(redisDB); for (final Entry<Integer, FailoverHandler> entry :
     * registers.entrySet()) { jedis.hset(hashName, entry.getKey() + "",
     * entry.getValue().getClass().getName()); } } finally { lock.unlock(); } }
     */
}
