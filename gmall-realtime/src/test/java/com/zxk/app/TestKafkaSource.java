package com.zxk.app;

import com.zxk.bean.Bean1;
import com.zxk.bean.Bean3;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class TestKafkaSource {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        SingleOutputStreamOperator<Bean3> bean3DS = env.socketTextStream("hadoop103", 8888)
                .map(line -> {
                    String[] strings = line.split(",");
                    return new Bean3(strings[0], strings[1], Long.parseLong(strings[2]));
                });
        tableEnv.createTemporaryView("t3",bean3DS);

        //创建Kafka表
        tableEnv.executeSql("" +
                "create table source_table(" +
                "    id string," +
                "    name string," +
                "    sex string" +
                ") " + MyKafkaUtil.getKafkaDDL("test","test-2022-07-31"));


        Table table = tableEnv.sqlQuery("select source_table.*,t3.money from source_table join t3 on source_table.id=t3.id");
        tableEnv.createTemporaryView("temp", table);
        tableEnv.toChangelogStream(table).print();

        //创建Kafka表
        tableEnv.executeSql("" +
                "create table result_table(" +
                "    id string," +
                "    name string," +
                "    sex string," +
                "    money string," +
                "    PRIMARY KEY (id) NOT ENFORCED " +
                ") " + MyKafkaUtil.getUpsertKafkaDDL("upsert_result_test"));

        //将数据写入Kafka
        tableEnv.executeSql("insert into result_table select * from temp");

        env.execute();
    }
}
