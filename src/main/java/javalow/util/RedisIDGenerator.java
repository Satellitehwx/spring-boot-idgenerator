package javalow.util;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

/**
 * @description: 分布式系统基于redis生成ID
 * @author: huweixing
 * @ClassName: RedisIDGenerator
 * @Date: 2020-06-02
 * @Time: 17:28
 */
@Component
public class RedisIDGenerator {

    static final Logger logger = LoggerFactory.getLogger(RedisIDGenerator.class);


    /*private static String host;
    private static String port;

    public static String getPort() {
        return port;
    }

    @Value("${redis.port}")
    public void setPort(String port) {
        RedisIDGenerator.port = port;
    }

    public static String getHost() {
        return host;
    }

    @Value("${redis.hostName}")
    public void setHost(String host) {
        RedisIDGenerator.host = host;
    }*/


    List<Pair<JedisPool, String>> jedisPoolList;
    int retryTimes;
    int index = 0;

    private RedisIDGenerator() {
    }

    private RedisIDGenerator(List<Pair<JedisPool, String>> jedisPoolList, int retryTimes) {
        this.jedisPoolList = jedisPoolList;
        this.retryTimes = retryTimes;
    }

    static public IdGeneratorBuilder builder() {
        return new IdGeneratorBuilder();
    }

    static class IdGeneratorBuilder {

        List<Pair<JedisPool, String>> jedisPoolList = new ArrayList<>();
        int retryTimes = 5;

        public IdGeneratorBuilder addHost(String host, int port, String pass, String luaSha) {
            JedisPoolConfig config = new JedisPoolConfig();
            //最大空闲连接数, 应用自己评估，不要超过ApsaraDB for Redis每个实例最大的连接数
            config.setMaxIdle(200);
            //最大连接数, 应用自己评估，不要超过ApsaraDB for Redis每个实例最大的连接数
            config.setMaxTotal(300);
            config.setTestOnBorrow(false);
            config.setTestOnReturn(false);
            config.setLifo(true);
            config.setMinIdle(30);
            jedisPoolList.add(Pair.of(StringUtils.isEmpty(pass) ? new JedisPool(config, host, port, 1000) : new JedisPool(config, host, port, 1000, pass), luaSha));
            return this;
        }

        public IdGeneratorBuilder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public RedisIDGenerator build() {
            return new RedisIDGenerator(jedisPoolList, retryTimes);
        }
    }

    public long next(String tab) {
        for (int i = 0; i < retryTimes; ++i) {
            Long id = innerNext(tab);
            if (id != null) {
                return id;
            }
        }
        throw new RuntimeException("Can not generate id!");
    }

    /**
     * 设置生成ID参数
     *
     * @param tab 生成id存放的序列
     * @return
     */
    private Long innerNext(String tab) {
        Calendar cal = Calendar.getInstance();
        //获取当前年份前两位
        String year = String.valueOf(cal.get(Calendar.YEAR)).substring(2);
        //获取今天在今年是第几天
        String day = String.valueOf(cal.get(Calendar.DAY_OF_YEAR));
        if (index == jedisPoolList.size())
            index = 0;
        Pair<JedisPool, String> pair = jedisPoolList.get(index++ % jedisPoolList.size());
        JedisPool jedisPool = pair.getLeft();
        String luaSha = pair.getRight();
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            Long result = Long.valueOf(jedis.evalsha(luaSha, 3, tab, year, day).toString());
            return result;
        } catch (JedisException e) {
            logger.error("generate id error!", e);
            return null;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * 加载客户端连接配置
     */
    static class LoadIdGeneratorConfig {
        static List<RedisScriptConfig> scriptConf = new ArrayList<>();
        static LoadIdGeneratorConfig loadConfig = new LoadIdGeneratorConfig();

        static {
            Properties pro = new Properties();
            try {
                //读取redis连接配置文件
                pro.load(LoadIdGeneratorConfig.class.getResourceAsStream("/cluster_redis_config.properties"));
                for (int i = 1; i <= 3; i++) {
                    String host = pro.getProperty("redis_cluster" + i + "_host");
                    String pass = pro.getProperty("redis_cluster" + i + "_pass");
                    if (StringUtils.isEmpty(host)) {
                        continue;
                    }
                    scriptConf.add(new RedisScriptConfig(host, Integer.valueOf(pro.getProperty("redis_cluster" + i + "_port", "6379")), pass));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            /*String[] hostArray = host.split(",");
            String[] portArray = port.split(",");
            if (hostArray.length != portArray.length) {
                throw new BusinessChanceException(ExceptionCodes.PRODUCT_140001);
            }
            for (int i = 0; i <= hostArray.length - 1; i++) {
                String ip = hostArray[i];
                String port = portArray[i];
                scriptConf.add(new RedisScriptConfig(ip.trim(), (Integer.parseInt(port) == 0 ? 6379 : Integer.parseInt(port)), null));
            }*/
        }

        public RedisIDGenerator buildIdGenerator() throws IOException {
            loadConfig.loadScript();
            IdGeneratorBuilder idGenerator = RedisIDGenerator.builder();
            for (RedisScriptConfig conf : scriptConf) {
                idGenerator = idGenerator.addHost(conf.getHost(), conf.getPort(), conf.getPass(), conf.getScriptSha());
            }
            return idGenerator.build();
        }

        /**
         * 加载生成id脚本文件
         *
         * @throws IOException
         */
        public void loadScript() throws IOException {
            int index = 1;
            for (RedisScriptConfig conf : scriptConf) {
                Jedis jedis = new Jedis(conf.getHost(), conf.getPort());
                if (!StringUtils.isEmpty(conf.getPass())) {
                    jedis.auth(conf.getPass());
                }
                InputStream is = LoadIdGeneratorConfig.class.getResourceAsStream("/script/redis-script-node" + index++ + ".lua");
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String readLine = null;
                StringBuilder sb = new StringBuilder();
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine);
                }
                br.close();
                is.close();
                conf.setScriptSha(jedis.scriptLoad(sb.toString()));
                jedis.close();
            }
        }
    }

    static class RedisScriptConfig {

        private String host;
        private Integer port;
        private String pass;
        private String scriptSha;

        public RedisScriptConfig(String host, Integer port, String pass) {
            super();
            this.host = host;
            this.port = port;
            this.pass = pass;
        }

        public void setScriptSha(String scriptSha) {
            this.scriptSha = scriptSha;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getPass() {
            return pass;
        }

        public String getScriptSha() {
            return scriptSha;
        }
    }
}
