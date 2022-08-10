package com.zxk.gmall.realtime.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import com.zxk.gmall.realtime.util.TimestampLtz3CompareUtil;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class OrderDetailFilterFunction {
    public static SingleOutputStreamOperator<JSONObject> getDwdOrderDetail(StreamExecutionEnvironment env, String groupId) {
        //TODO 2.读取Kafka DWD层 订单明细主题数据

        String topic = "dwd_trade_order_detail";
        DataStreamSource<String> orderDetailStrDS= env.addSource(MyKafkaUtil.getKafkaConsumer(topic, groupId));

        SingleOutputStreamOperator<JSONObject> jsonObjDS = orderDetailStrDS.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String s, Collector<JSONObject> collector) throws Exception {
                //过滤撤回数据
                if (!"".equals(s)) {
                    JSONObject jsonObject = JSON.parseObject(s);
                    String type = jsonObject.getString("type");
                    //保留新增类型数据
                    if ("insert".equals(type)) {
                        collector.collect(jsonObject);
                    }
                }
            }
        });

        //按主键分组
        KeyedStream<JSONObject, String> keyedStream = jsonObjDS.keyBy(jsonObject -> jsonObject.getString("order_detail_id"));

        //组内去重
        SingleOutputStreamOperator<JSONObject> orderDetailJsonObjDS = keyedStream.process(new KeyedProcessFunction<String, JSONObject, JSONObject>() {
            ValueState<JSONObject> state;

            @Override
            public void open(Configuration parameters) throws Exception {
                state = getRuntimeContext().getState(new ValueStateDescriptor<JSONObject>("value", JSONObject.class));
            }

            @Override
            public void processElement(JSONObject value, Context ctx, Collector<JSONObject> out) throws Exception {
                JSONObject orderDetail = state.value();

                //分区首条合法数据,更新状态,注册定时器
                if (orderDetail == null) {
                    state.update(value);
                    ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 2000L);
                    //与状态的值比较,保留时间戳大的
                } else {
                    String stateTs = orderDetail.getString("ts");

                    String curTs = value.getString("ts");

                    int compare = TimestampLtz3CompareUtil.compare(stateTs, curTs);
                    if (compare != 1) {
                        state.update(value);
                    }
                }
            }

            @Override
            //定时器触发,输出状态中数据
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<JSONObject> out) throws Exception {
                JSONObject orderDetail = state.value();
                out.collect(orderDetail);
            }
        });

        return orderDetailJsonObjDS;
    }
}
