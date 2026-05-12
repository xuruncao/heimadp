package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查出秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断活动是否开始
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动未开始！");
        }
        //3.判断活动是否结束
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已结束！");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createOrder(voucherId);
        }
    }

    @Transactional
    public Result createOrder(Long voucherId){

        Long userId = UserHolder.getUser().getId();

            int count = query().eq("id",userId).eq("voucher_id",voucherId).count();

            if(count > 0){
                return Result.fail("用户已经购买过一次！");
            }

            //4.判断是否有库存剩余
            //5.扣除库存
            seckillVoucherService.update()
                    .setSql("Stock = Stock - 1")
                    .eq("voucher_id",voucherId)
                    .gt("stock",0)
                    .update();
            //6一人一单功能

            // 6. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();

            // 6.1. 订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            // 6.2. 用户id

            voucherOrder.setUserId(userId);
// 6.3. 代金券id
            voucherOrder.setVoucherId(voucherId);

            //插入订单数据库
            save(voucherOrder);
            return Result.ok(orderId);


    }

}
