package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * Redis 缓存工具类
 *
 * 作用：
 * 1. 封装 Redis 的常用缓存写入逻辑，避免业务层直接操作 StringRedisTemplate
 * 2. 提供普通缓存写入
 * 3. 提供逻辑过期缓存写入，用于解决缓存击穿问题
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 提供正常过期缓存写入逻辑
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    /**
     * 提供逻辑过期写入逻辑
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 提供解决缓存击穿查询
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                           Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1.判断redis中是否能查到
        String json = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isBlank(json)){
            //2.查不到，直接返回
            return null;
        }
        //3 查到
        // 3.1 将json类型的数据转化成redisData类型
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        // 3.2 判断是否逻辑过期
        if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
            //4未过期，直接返回
            return r;
        }
        //5.过期，则尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = getLock(lockKey);
        //6判断是否获取成功
        if(isLock){
            //6.1成功，看是否缓存里有未过期数据，有的话直接返回
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json,RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(),type);

            if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
                return r;
            }
            //6.2 没有过期数据，则尝试获取一个新的线程进行数据更新。
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //7.返回过期店铺信息

        return r;
    }


    /**
     * 提供解决缓存穿透查询
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1.判断redis中是否能查到
        String json= stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(json)){
            //2.查到，直接返回
            return JSONUtil.toBean(json, type);
        }
        //如果值为“”,返回不存在
        if(json != null){
            return null;
        }
        //3.查不到，查数据库
        R r = dbFallback.apply(id);
        //5.查不到，返回404
        if(r == null){
            //新增：为了防止缓存穿透，讲不存在的存为空值。
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //4.数据库查到
        //5.把数据保存到redis中
       this.set(key,r, time, timeUnit);
        //，返回
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    public Boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 解锁
     * @param key
     */
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
