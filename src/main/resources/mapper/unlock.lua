-- 保证判断与释放锁的原子性，防止其他的线程在某些情况下在判断与释放锁的间隙获取到锁，然后又导致误删。
-- 比较线程标识与锁中的标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0