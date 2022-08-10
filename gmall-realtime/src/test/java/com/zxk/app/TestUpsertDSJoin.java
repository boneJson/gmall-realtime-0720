package com.zxk.app;

import com.zxk.bean.Bean1;
import com.zxk.bean.Bean2;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

public class TestUpsertDSJoin {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        //TTL状态存活时间,仅时间,没说重置
        System.out.println(tableEnv.getConfig().getIdleStateRetention());
        tableEnv.getConfig().setIdleStateRetention(Duration.ofHours(10));

        //TODO 1.source
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

        //TODO 2.process
        Table tmp1 = tableEnv.sqlQuery(
                "select "
                        + "id, "
                        + "name, "
                        + "ts "
                        + "from ( "
                        + "select "
                        + "id, "
                        + "name, "
                        + "ts, "
                        + "row_number()over(partition by id order by ts desc) rk "
                        + "from t1 "
                        + ") tmp1  "
                        + "where rk =1 "
        );
        tableEnv.createTemporaryView("tmp1",tmp1);
        Table tmp2 = tableEnv.sqlQuery(
                "select "
                        + "id, "
                        + "sex, "
                        + "ts "
                        + "from ( "
                        + "select "
                        + "id, "
                        + "sex, "
                        + "ts, "
                        + "row_number()over(partition by id order by ts desc) rk "
                        + "from t2 "
                        + ") tmp2  "
                        + "where rk =1 "
        );
        tableEnv.createTemporaryView("tmp2",tmp2);
        //TODO 3.sink
        //创建Kafka表
        tableEnv.executeSql("" +
                "create table result_table(" +
                "    id string," +
                "    name string," +
                "    tmp1ts bigint," +
                "    tmp2id string," +
                "    tmp2name string," +
                "    tmp2ts bigint," +
                "    PRIMARY KEY (id) NOT ENFORCED " +
                ") " + MyKafkaUtil.getUpsertKafkaDDL("test"));

        //将数据写入Kafka
        tableEnv.executeSql(
                "insert into result_table "+
                     "select "
                        + "tmp1.id, "
                        + "tmp1.name, "
                        + "tmp1.ts as tmp1ts, "
                        + "tmp2.id, "
                        + "tmp2.sex, "
                        + "tmp2.ts tmp2ts "
                        + "from tmp1 "
                        + "left join tmp2 "
                        + "on tmp1.id = tmp2.id "
        ).print();
    }
}
