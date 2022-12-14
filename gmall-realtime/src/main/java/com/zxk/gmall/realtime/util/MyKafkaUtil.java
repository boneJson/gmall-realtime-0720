package com.zxk.gmall.realtime.util;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Properties;
//消费kafka/生产
//kafka-console-producer.sh --broker-list hadoop102:9092 --topic test
public class MyKafkaUtil {
    private static Properties properties = new Properties();
    private static final String BOOTSTRAP_SERVERS = "hadoop102:9092";
    static {
        //生产和消费底层共有的
        properties.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    }

    //工具类中使用静态方法
    public static FlinkKafkaConsumer<String> getKafkaConsumer(String topic, String group_id) {

        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, group_id);

        return new FlinkKafkaConsumer<String>(topic,
                //这里不使用SimpleStringSchema实现反序列化,是因为其限制了输入为NotNull,否则抛异常
                new KafkaDeserializationSchema<String>() {
                    @Override
                    public boolean isEndOfStream(String s) {
                        return false;
                    }

                    @Override
                    public String deserialize(ConsumerRecord<byte[], byte[]> consumerRecord) throws Exception {
                        //将null值转为空串
                        if (consumerRecord == null || consumerRecord.value() == null) {
                            return "";
                        } else {
                            return new String(consumerRecord.value());
                        }
                    }

                    @Override
                    public TypeInformation<String> getProducedType() {
                        return BasicTypeInfo.STRING_TYPE_INFO;
                    }
                },
                properties
        );
    }

    public static FlinkKafkaProducer<String> getKafkaProducer(String topic) {
        return new FlinkKafkaProducer<String>(topic,
                new SimpleStringSchema(),
                properties);
    }

    public static String getUpsertKafkaDDL(String topic) {
        return " with ('connector' = 'upsert-kafka', " +
                " 'topic' = '" + topic + "'," +
                " 'properties.bootstrap.servers' = '" + BOOTSTRAP_SERVERS + "', " +
                "  'key.format' = 'json', " +
                "  'value.format' = 'json' "+
                ")";
    }


    public static String getKafkaDDL(String topic, String groupId) {
        return " with ('connector' = 'kafka', " +
                " 'topic' = '" + topic + "'," +
                " 'properties.bootstrap.servers' = '" + BOOTSTRAP_SERVERS + "', " +
                " 'properties.group.id' = '" + groupId + "', " +
                " 'format' = 'json', " +
                " 'scan.startup.mode' = 'latest-offset')";
//                " 'scan.startup.mode' = 'earliest-offset')";
    }

    public static String getTopicDbDDL(String groupId) {
        return "CREATE TABLE topic_db ( " +
                " `database` STRING, " +
                " `table` STRING, " +
                " `type` STRING, " +
                " `data` Map<STRING,STRING>, " +
                " `old` Map<STRING,STRING>, " +
                " `pt` AS PROCTIME() " +
                ")"+getKafkaDDL("topic_db",groupId);
    }
}
