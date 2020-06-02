import javalow.util.BuildIDFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @description: 测试id生成
 * @author: huweixing
 * @ClassName: RedisIDGeneratorTest
 * @Date: 2020-06-02
 * @Time: 17:50
 */
public class RedisIDGeneratorTest {

    public static void main(String[] args) {
        long current = System.currentTimeMillis();
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Long id = BuildIDFactory.getInstance().buildFactoryOrderId();
            ids.add(id);
            System.out.println(id);
        }
        System.out.println("ids >>>>>>" + ids.size());
        System.err.println(System.currentTimeMillis() - current);
    }

}
