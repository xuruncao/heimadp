package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
    /**
     * 查询商铺信息时redis缓存
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = "cache:shop:" + id;
        //1.判断redis中是否能查到
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(shopJSON)){
            //2.查到，直接返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        //3.查不到，查数据库
        Shop shop = getById(id);
        //5.查不到，返回404
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //4.数据库查到
        //5.把数据保存到redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //，返回
        return Result.ok(shop);
    }
}
