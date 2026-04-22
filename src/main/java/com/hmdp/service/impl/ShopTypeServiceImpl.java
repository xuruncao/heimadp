package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 获取店铺列表
     * @return
     */
    @Override
    public Result getTypeList() {
        String key = "cache:typeList";

        //查询缓存
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);

        //存在，直接返回
        if(StrUtil.isNotBlank(shopTypeListJson)){
            List<ShopType> typeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(typeList);
        }
        //不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //存在
        //保存到缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
