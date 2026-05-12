package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.lettuce.core.RedisClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 查询商铺信息时redis缓存
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透问题
         Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,
                 RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //解决缓存击穿问题
        //Shop shop = queryWithMutex(id);
        //用逻辑过期解决缓存击穿问题
         /*Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,
                 RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);*/
        //，返回
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }


   /* public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.判断redis中是否能查到
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isBlank(shopJSON)){
            //2.查不到，直接返回
            return null;
        }
        //3 查到
        // 3.1 将json类型的数据转化成redisData类型
        RedisData redisData = JSONUtil.toBean(shopJSON,RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        // 3.2 判断是否逻辑过期
        if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
            //4未过期，直接返回
            return shop;
        }
        //5.过期，则尝试获取互斥锁
        String lockKey = "lock:shop:" + id;
        Boolean isLock = getLock(lockKey);
        //6判断是否获取成功
        if(isLock){
            //6.1成功，看是否缓存里有未过期数据，有的话直接返回
            shopJSON = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(shopJSON,RedisData.class);
            shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);

            if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
                return shop;
            }
            //6.2 没有过期数据，则尝试获取一个新的线程进行数据更新。
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        //7.返回过期店铺信息

        return shop;
    }*/


    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.判断redis中是否能查到
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(shopJSON)){
            //2.查到，直接返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return shop;
        }
        //如果值为“”,返回不存在
        if(shopJSON != null){
            return null;
        }
        //4解决缓存击穿问题
        Shop shop = null;
        String lockKey = "lock:shop:" + id;
        try {
            //4.1尝试获取锁
            Boolean lock = getLock(lockKey);
            //4.2判断是否获取锁成功
            if(!lock){
                //4.3失败，则休眠一段时间，继续尝试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            //4.4，成功，则再次判断缓存中是否有数据！！！
            shopJSON = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJSON)){
                shop = JSONUtil.toBean(shopJSON, Shop.class);
                return shop;
            }
            if(shopJSON != null){
                return null;
            }
            //4.6如果没有，则查询数据库
            shop = getById(id);
            //5.查不到，返回404
            if(shop == null){
                //新增：为了防止缓存穿透，将不存在的存为空值。
                stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.数据库查到
            //7.把数据保存到redis中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        //8.释放锁
        return shop;
    }

   /* public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.判断redis中是否能查到
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(shopJSON)){
            //2.查到，直接返回
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        //如果值为“”,返回不存在
        if(shopJSON != null){
            return null;
        }
        //3.查不到，查数据库
        Shop shop = getById(id);
        //5.查不到，返回404
        if(shop == null){
            //新增：为了防止缓存穿透，讲不存在的存为空值。
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //4.数据库查到
        //5.把数据保存到redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //，返回
        return shop;
    }*/

    public void saveShop2Redis(Long id,Long expireSeconds){
        //根据id查询店铺
        Shop shop = getById(id);
        //设计逻辑缓存时间，并且整合进RedisData对象
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //将redisData对象，插入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺不能为空！");
        }
        String key = "cache:shop:" +id ;
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(key);
        return null;
    }


    public Boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }



}
