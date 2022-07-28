package com.zxk.app;

import com.zxk.bean.WaterSensor;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import static org.apache.flink.table.api.Expressions.$;

public class LookUpJoinTest {
    public static void main(String[] args) {
        StreamExecutionEnvironment e = StreamExecutionEnvironment.getExecutionEnvironment();
        e.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(e);

        SingleOutputStreamOperator<WaterSensor> waterSensorDS = e.socketTextStream("hadoop102", 8888)
                .map(line -> {
                    String[] split = line.split(",");
                    return new WaterSensor(split[0],
                            Double.parseDouble(split[1]),
                            Long.parseLong(split[2]));
                });

        Table table = tableEnv.fromDataStream(waterSensorDS,
                $("id"),
                $("vc"),
                $("ts"),
                $("pt").proctime());
        tableEnv.createTemporaryView("t1", table);

        //创建LookUp表
        tableEnv.executeSql("" +
                "CREATE TEMPORARY TABLE my_base_dic ( " +
                "  dic_code STRING, " +
                "  dic_name STRING " +
                ") WITH ( " +
                "  'connector' = 'jdbc', " +
                "  'url' = 'jdbc:mysql://hadoop102:3306/gmall', " +
                "  'username' = 'root', " +
                "  'password' = '123456', " +
                "  'lookup.cache.max-rows' = '10', " +
                "  'lookup.cache.ttl' = '1 hour', " +
                "  'driver' = 'com.mysql.cj.jdbc.Driver', " +
                "  'table-name' = 'base_dic' " +
                ")");

        //关联并打印
        tableEnv.sqlQuery("" +
                "select " +
                "    t1.id, " +
                "    t1.vc, " +
                "    t2.dic_name " +
                "from t1 " +
                "join my_base_dic FOR SYSTEM_TIME AS OF t1.pt as t2 " +
                "on t1.id = t2.dic_code")
                .execute()
                .print();

    }
}
