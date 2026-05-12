package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long startTime = 1767225600L;


    public long nextId(String keyPrefix) {
        //1.获取时间戳
        LocalDateTime localDateTime = LocalDateTime.now();
        long timeStamp = localDateTime.toEpochSecond(ZoneOffset.UTC) - startTime;

        //2.获取序列号
        //2.1获取当前日期作为key，防止一个key，然后达到redis数据上限
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix +":" + date);

        return timeStamp << 32 | count;
    }


}