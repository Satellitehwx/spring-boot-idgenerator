生成ID格式 
20154000000000003 

20            当前年份最后两位 

154           几天在今年是第几天 

000000000003  基于redis生成的递增序列

cluster_redis_config.properties配置文件中配置单机或集群redis

redis-script-node.lua 文件 与 redis服务文件一一对应 一台redis对应一个lua文件配置 

startStep 为redis机器生成id的起始值 

step 为步长，一般n台redis步长设置为 n 

string.format 为脚本最终拼接ID的方法 string.format('%02d', redis.call('GET',prefix..tag..':year')) 意思为 前两位为年份(可修改02d为04d在RedisIDGenerator.innerNext()中的year参数改为获取完整年份，同理其他几个参数均可自定义)

RedisIDGenerator.innerNext() 方法中year和day参数是组成ID的重要参数可根据自己的业务自定义

BuildIDFactory 类中 TAB_ORDER 常量为ID生成所在序列(同一序列当前面生成的ID格式固定修改lua文件生成ID格式不会改变续修改序列)

SnowflakeIdGenerator  Twitter公司基于雪花算法生成ID 强依赖服务器时间，时间回拨可能导致ID冲突