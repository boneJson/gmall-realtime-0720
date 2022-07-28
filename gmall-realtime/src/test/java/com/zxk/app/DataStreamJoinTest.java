package com.zxk.app;

import com.zxk.bean.Bean1;
import com.zxk.bean.Bean2;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

public class DataStreamJoinTest {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Bean1> bean1DS = env.socketTextStream("hadoop102", 8888)
                .map(line -> {
                    String[] strings = line.split(",");
                    return new Bean1(strings[0], strings[1], Long.parseLong(strings[2]));
                }).assignTimestampsAndWatermarks(WatermarkStrategy.<Bean1>forMonotonousTimestamps()
                        .withTimestampAssigner(new SerializableTimestampAssigner<Bean1>() {
                            @Override
                            public long extractTimestamp(Bean1 bean1, long l) {
                                return bean1.getTs() * 1000L;
                            }
                        }));

        SingleOutputStreamOperator<Bean2> bean2DS = env.socketTextStream("hadoop102", 9999)
                .map(line -> {
                    String[] strings = line.split(",");
                    return new Bean2(strings[0], strings[1], Long.parseLong(strings[2]));
                }).assignTimestampsAndWatermarks(WatermarkStrategy.<Bean2>forMonotonousTimestamps()
                        .withTimestampAssigner(new SerializableTimestampAssigner<Bean2>() {
                            @Override
                            public long extractTimestamp(Bean2 bean1, long l) {
                                return bean1.getTs() * 1000L;
                            }
                        }));

        //双流join
        SingleOutputStreamOperator<Tuple2<Bean1, Bean2>> result = bean1DS.keyBy(Bean1::getId)
                .intervalJoin(bean2DS.keyBy(Bean2::getId))
                .between(Time.seconds(-5), Time.seconds(5))
                .process(new ProcessJoinFunction<Bean1, Bean2, Tuple2<Bean1, Bean2>>() {
                    @Override
                    public void processElement(Bean1 left, Bean2 right, Context ctx, Collector<Tuple2<Bean1, Bean2>> out) throws Exception {
                        out.collect(Tuple2.of(left, right));
                    }
                });

        result.print(">>>>>>>>>>>");

        env.execute();
    }
}
