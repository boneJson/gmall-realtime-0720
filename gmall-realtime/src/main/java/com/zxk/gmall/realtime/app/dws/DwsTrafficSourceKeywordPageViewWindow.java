package com.zxk.gmall.realtime.app.dws;

import com.zxk.gmall.realtime.app.func.SplitFunction;
import com.zxk.gmall.realtime.bean.KeywordBean;
import com.zxk.gmall.realtime.util.MyClickHouseUtil;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

//流量域来源关键词粒度页面浏览各窗口汇总表
public class DwsTrafficSourceKeywordPageViewWindow {
    public static void main(String[] args) throws Exception {
        //TODO  1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // TODO 2. 状态后端设置
//        env.enableCheckpointing(3000L, CheckpointingMode.EXACTLY_ONCE);
//        env.getCheckpointConfig().setCheckpointTimeout(60 * 1000L);
//        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000L);
//        env.getCheckpointConfig().enableExternalizedCheckpoints(
//                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
//        );
//        env.setRestartStrategy(RestartStrategies.failureRateRestart(
//                3, Time.days(1), Time.minutes(1)
//        ));
//        env.setStateBackend(new HashMapStateBackend());
//        env.getCheckpointConfig().setCheckpointStorage(
//                "hdfs://hadoop102:8020/ck"
//        );
//        System.setProperty("HADOOP_USER_NAME", "atguigu");

        //TODO 2.使用DDL方式读取 DWD层页面浏览日志创建表,同时获取事件时间生成Watermark
        //建表仅保留分析内容的page字段,和事件时间的ts字段
        //建表DDL中定义事件时间:需要TIMESTAMP类型-->TIMESTAMP生成函数TO_TIMESTAMP(string1[, string2]),输入为年月日时分秒字符串,
        //-->日期字符串生成函数FROM_UNIXTIME(numeric[, string]),输入为s级长整型,所以需要ts/1000
        tableEnv.executeSql("" +
                "create table page_log( " +
                "    `page` map<string,string>, " +
                "    `ts` bigint, " +
                "    `rt` as TO_TIMESTAMP(FROM_UNIXTIME(ts/1000)), " +
                "    WATERMARK FOR rt AS rt - INTERVAL '2' SECOND " +
                ")" + MyKafkaUtil.getKafkaDDL("dwd_traffic_page_log", "Dws_Traffic_Source_Keyword_PageView_Window"));

        //TODO 3.过滤出搜索数据
        Table keyWordTable = tableEnv.sqlQuery("" +
                "select " +
                "    page['item'] key_word, " +
                "    rt " +
                "from " +
                "    page_log " +
                "where page['item'] is not null " +
                "and page['last_page_id'] = 'search' " +
                "and page['item_type'] = 'keyword'");
        tableEnv.createTemporaryView("key_word_table", keyWordTable);

        //TODO 4.使用自定义函数分词处理
        //4.1 注册函数
        tableEnv.createTemporaryFunction("SplitFunction", SplitFunction.class);
        //4.2 处理数据
        //自定义函数搭配侧写表使用,选择时注意原表和侧写表中的原字段和函数输出字段,rt为开窗使用的事件时间字段,保留
        Table splitTable = tableEnv.sqlQuery("" +
                "SELECT  " +
                "    word, " +
                "    rt " +
                "FROM key_word_table, LATERAL TABLE(SplitFunction(key_word))");
        tableEnv.createTemporaryView("split_table", splitTable);

        //TODO 5.分组开窗聚合
        //补充窗口时间作为下游去重字段,使用函数转为时间字符串,传参的格式字符串不可省
        //由于输出表的粒度为来源关键词粒度,补充来源字段
        //对应输出的类型补充ts字段,使用时间戳生成函数,s级长整型时间戳
        //注意:输出类型的各字段名称需要和列名对应,而不是位置,所以别名保持一致
        Table resultTable = tableEnv.sqlQuery("" +
                "select " +
                "    'search' source, " +
                "    DATE_FORMAT(TUMBLE_START(rt, INTERVAL '10' SECOND),'yyyy-MM-dd HH:mm:ss') stt, " +
                "    DATE_FORMAT(TUMBLE_END(rt, INTERVAL '10' SECOND),'yyyy-MM-dd HH:mm:ss') edt, " +
                "    word keyword, " +
                "    count(*) keyword_count, " +
                "    UNIX_TIMESTAMP() ts " +
                "from " +
                "    split_table " +
                "group by TUMBLE(rt, INTERVAL '10' SECOND),word");

        //TODO 6.将数据转换为流
        //虽然聚合,但是开窗了,一个窗口就一个结果,没有撤回,转为追加流
        DataStream<KeywordBean> keywordBeanDataStream = tableEnv.toAppendStream(resultTable, KeywordBean.class);
        keywordBeanDataStream.print(">>>>>>>>>>");

        //TODO 7.将数据写出到ClickHouse
        keywordBeanDataStream.addSink(MyClickHouseUtil.getClickHouseSink("insert into dws_traffic_source_keyword_page_view_window values(?,?,?,?,?,?)"));

        //TODO 8.启动任务
        env.execute("DwsTrafficSourceKeywordPageViewWindow");
    }
}
