package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号格式，不符合直接返回
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");      //思考： 返回result.fail和用异常类有什么区别？
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到redis
        String token = UUID.randomUUID().toString(true);

        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL,TimeUnit.MINUTES);

        //4.发送验证码，本来是要调用服务，这边简略的在log里面打印就好
        log.info("发送短信验证码成功！，验证码为：{}",code);

        //封装成Result对象返回
        return Result.ok();
    }

    /**
     * 登录以及注册功能    （如果未注册就直接注册）
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.校验手机号和验证码（用redis校验）
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");      //思考： 返回result.fail和用异常类有什么区别？
        }

        //TODO 这里的code要强转字符串
        String code = loginForm.getCode();
        String s = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if( s == null || !code.equals(s)){
            //2.不一致报错
            return  Result.fail("验证码错误！");

        }

        //从数据库里查询用户是否存在
        User user = query().eq("phone", phone).one();

        //不存在，创建新的用户，就保存到数据库
        if(user == null){
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10) );
            user.setCreateTime(LocalDateTime.now());
            save(user);
        }


        // 保存到Redis,这里是用来，辅助用户下次登录的
       String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );

        String tokenKey = LOGIN_USER_KEY+token;

        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }


}
