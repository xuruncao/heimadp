package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 获取分布式锁（redis里面的set nx，以及加上自动过期）
 */
public class SimpleRedisLock implements ILock{

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate,String name){
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;

    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;//加载脚本使用的工具，Java 这边希望拿到的返回值类型是 Long。

    //初始化.一个是确定返回值的类型（在收到返回值的时候强转成Long，另外一个是确定脚本的位置）
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(b);
    }


    //调用lua脚本执行删除锁功能。
    @Override
    public void unlock() {
            stringRedisTemplate.execute(UNLOCK_SCRIPT,
                    Collections.singletonList(KEY_PREFIX + name),
                    Collections.singletonList(ID_PREFIX + Thread.currentThread().getId()));
        }

   /* @Override
    public void unlock() {

        Long threadId = Thread.currentThread().getId();
        if(threadId.toString().equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name))){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }*/

}
