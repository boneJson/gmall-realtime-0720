package com.zxk.app;

import com.zxk.bean.Bean1;
import com.zxk.bean.Bean2;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

public class FlinkSQLJoinTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        //TTL状态存活时间,仅时间,没说重置
        System.out.println(tableEnv.getConfig().getIdleStateRetention());
        tableEnv.getConfig().setIdleStateRetention(Duration.ofSeconds(10));

        SingleOutputStreamOperator<Bean1> bean1DS = env.socketTextStream("hadoop102", 8888)
                .map(line -> {
                    String[] strings = line.split(",");
                    return new Bean1(strings[0], strings[1], Long.parseLong(strings[2]));
                });

        SingleOutputStreamOperator<Bean2> bean2DS = env.socketTextStream("hadoop102", 9998)
                .map(line -> {
                    String[] strings = line.split(",");
                    return new Bean2(strings[0], strings[1], Long.parseLong(strings[2]));
                });

        tableEnv.createTemporaryView("t1",bean1DS);
        tableEnv.createTemporaryView("t2", bean2DS);

        //内联结       左表：OnCreateAndWrite   右表：OnCreateAndWrite
        /*tableEnv.sqlQuery("select t1.id,t1.name,t2.sex from t1 join t2 on t1.id=t2.id")
            .execute()
            .print();*/

        //左外联结      左表：OnReadAndWrite     右表：OnCreateAndWrite
        /*tableEnv.sqlQuery("select t1.id,t1.name,t2.sex from t1 left join t2 on t1.id=t2.id")
            .execute()
            .print();*/

        //右外连接     左表：OnCreateAndWrite   右表：OnReadAndWrite
        /*tableEnv.sqlQuery("select t2.id,t1.name,t2.sex from t1 right join t2 on t1.id = t2.id")
                .execute()
                .print();*/

        //全外连接     左表：OnReadAndWrite     右表：OnReadAndWrite
        /*tableEnv.sqlQuery("select t1.id,t2.id,t1.name,t2.sex from t1 full join t2 on t1.id = t2.id")
                .execute()
                .print();*/


        Table table = tableEnv.sqlQuery("select t1.id,t1.name,t2.sex from t1 left join t2 on t1.id = t2.id");
//        DataStream<Tuple2<Boolean, Row>> retractStream = tableEnv.toRetractStream(table, Row.class);
//        retractStream.print(">>>>>>>>>");
        tableEnv.createTemporaryView("t", table);

        //创建Kafka表
        tableEnv.executeSql("" +
                "create table result_table(" +
                "    id string," +
                "    name string," +
                "    sex string," +
                "    PRIMARY KEY (id) NOT ENFORCED " +
                ") " + MyKafkaUtil.getUpsertKafkaDDL("test"));

        //将数据写入Kafka
        tableEnv.executeSql("insert into result_table select * from t")
                .print();

        env.execute();
    }
}
