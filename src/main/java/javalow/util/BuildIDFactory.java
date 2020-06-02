package javalow.util;

import java.io.IOException;

/**
 * @description: ID生成类
 * @author: huweixing
 * @ClassName: BuildIDFactory
 * @Date: 2020-06-02
 * @Time: 17:52
 */
public class BuildIDFactory {
    /**
     * 序列
     */
    private final static String TAB_ORDER = "";

    private static volatile RedisIDGenerator idGenerator;
    private static volatile BuildIDFactory instance;

    private BuildIDFactory() {
    }

    public static BuildIDFactory getInstance() {
        if (idGenerator == null) {
            synchronized (RedisIDGenerator.LoadIdGeneratorConfig.class) {
                try {
                    idGenerator = RedisIDGenerator.LoadIdGeneratorConfig.loadConfig.buildIdGenerator();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (instance == null) {
            synchronized (BuildIDFactory.class) {
                instance = new BuildIDFactory();
            }
        }
        return instance;
    }

    public Long buildFactoryOrderId() {
        return idGenerator.next(TAB_ORDER);
    }


}
