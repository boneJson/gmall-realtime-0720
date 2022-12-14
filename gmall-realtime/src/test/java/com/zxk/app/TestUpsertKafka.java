package com.zxk.app;

import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class TestUpsertKafka {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        //创建Kafka表
        tableEnv.executeSql("" +
                "create table result_table (" +
                        "    id string," +
                        "    name string," +
                        "    tmp1ts bigint," +
                        "    tmp2id string," +
                        "    tmp2name string," +
                        "    tmp2ts bigint" +
                ") "+
                " with ('connector' = 'kafka', " +
                " 'topic' = 'test'," +
                " 'properties.group.id' = 'test-2022-07-31', " +
                " 'properties.bootstrap.servers' = 'hadoop102:9092 ', " +
//                "  'key.format' = 'json', " +
                " 'json.ignore-parse-errors' = 'false',"+
                "  'format' = 'json', "+
                " 'scan.startup.mode' = 'latest-offset'"+
        ")"
        );


        tableEnv.sqlQuery("select * from result_table").execute().print();

        env.execute();
    }
}
