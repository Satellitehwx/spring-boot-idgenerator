package javalow.util;

/**
 * Twitter_Snowflake<br>
 * SnowFlake的结构如下(每部分用-分开):<br>
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000 <br>
 * 1位标识，由于long基本类型在Java中是带符号的，最高位是符号位，正数是0，负数是1，所以id一般是正数，最高位是0<br>
 * 41位时间截(毫秒级)，注意，41位时间截不是存储当前时间的时间截，而是存储时间截的差值（当前时间截 - 开始时间截)
 * 得到的值），这里的的开始时间截，一般是我们的id生成器开始使用的时间，由我们程序来指定的
 * （如下下面程序IdWorker类的startTime属性）。41位的时间截，可以使用69年，
 * 年T = (1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69<br>
 * 10位的数据机器位，可以部署在1024个节点，包括5位datacenterId和5位workerId<br>
 * 12位序列，毫秒内的计数，12位的计数顺序号支持每个节点每毫秒(同一机器，同一时间截)产生4096个ID序号<br>
 * 加起来刚好64位，为一个Long型。<br>
 * SnowFlake的优点是，整体上按照时间自增排序，并且整个分布式系统内不会产生ID碰撞(由数据中心ID和机器ID作区分)，
 * 并且效率较高，经测试，SnowFlake每秒能够产生26万ID左右。
 *
 * @anthor huweixing
 * SnowflakeIdGenerator
 * @date 2020-05-09-19:18
 **/
public class SnowflakeIdGenerator {

    // 系统开始时间截 (UTC 2017-06-28 00:00:00)
    private final long startTime = 1498608000000L;

    // 机器id所占的位数
    private final long workerIdBits = 5L;

    // 数据标识id所占的位数
    private final long dataCenterIdBits = 5L;

    // 支持的最大机器id(十进制)，结果是31 (这个移位算法可以很快的计算出几位二进制数所能表示的最大十进制数)
    // -1L 左移 5位 (worker id 所占位数) 即 5位二进制所能获得的最大十进制数 - 31
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);

    // 支持的最大数据标识id - 31
    private final long maxDataCenterId = -1L ^ (-1L << dataCenterIdBits);

    // 序列在id中占的位数
    private final long sequenceBits = 12L;

    // 机器ID 左移位数 - 12 (即末 sequence 所占用的位数)
    private final long workerIdMoveBits = sequenceBits;

    // 数据标识id 左移位数 - 17(12+5)
    private final long dataCenterIdMoveBits = sequenceBits + workerIdBits;

    // 时间截向 左移位数 - 22(5+5+12)
    private final long timestampMoveBits = sequenceBits + workerIdBits + dataCenterIdBits;

    // 生成序列的掩码(12位所对应的最大整数值)，这里为4095 (0b111111111111=0xfff=4095)
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    /**
     * 工作机器ID(0~31)
     */
    private long workerId;

    /**
     * 数据中心ID(0~31)
     */
    private long dataCenterId;

    /**
     * 毫秒内序列(0~4095)
     */
    private long sequence = 0L;

    /**
     * 上次生成ID的时间截
     */
    private long lastTimestamp = -1L;

    /**
     * 构造函数
     *
     * @param workerId     工作ID (0~31)
     * @param dataCenterId 数据中心ID (0~31)
     */
    public SnowflakeIdGenerator(long workerId, long dataCenterId) {

        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("Worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (dataCenterId > maxDataCenterId || dataCenterId < 0) {
            throw new IllegalArgumentException(String.format("DataCenter Id can't be greater than %d or less than 0", maxDataCenterId));
        }

        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

// ==================================================Methods========================================================

    // 线程安全的获得下一个 ID 的方法
    synchronized long nextId() {
        long timestamp = currentTime();

        //如果当前时间小于上一次ID生成的时间戳: 说明系统时钟回退过 - 这个时候应当抛出异常
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        //如果是同一时间生成的，则进行毫秒内序列
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            //毫秒内序列溢出 即 序列 > 4095
            if (sequence == 0) {
                //阻塞到下一个毫秒,获得新的时间戳
                timestamp = blockTillNextMillis(lastTimestamp);
            }
        } else {
            //时间戳改变，毫秒内序列重置
            sequence = 0L;
        }
        //上次生成ID的时间截
        lastTimestamp = timestamp;
        //移位并通过或运算拼到一起组成64位的ID
        return ((timestamp - startTime) << timestampMoveBits) | (dataCenterId << dataCenterIdMoveBits) | (workerId << workerIdMoveBits) | sequence;
    }

    // 阻塞到下一个毫秒 即 直到获得新的时间戳
    protected long blockTillNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    // 获得以毫秒为单位的当前时间
    protected long currentTime() {
        return System.currentTimeMillis();
    }

//====================================================Test Case=====================================================

    public static void main(String[] args) {
        SnowflakeIdGenerator idWorker = new SnowflakeIdGenerator(0, 0);
        for (int i = 0; i < 100; i++) {
            long id = idWorker.nextId();
            //System.out.println(Long.toBinaryString(id));
            System.out.println(id);
        }

    }


}
