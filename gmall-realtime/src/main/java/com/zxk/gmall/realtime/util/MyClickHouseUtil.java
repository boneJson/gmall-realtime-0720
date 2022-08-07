package com.zxk.gmall.realtime.util;

import com.zxk.gmall.realtime.bean.TransientSink;
import com.zxk.gmall.realtime.common.GmallConfig;
import lombok.SneakyThrows;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MyClickHouseUtil {

    //参考官网的JdbcSink.sink返回值类型
    //返回值的泛型不确定,因此使用泛型T,泛型方法必须声明泛型,因此返回类型前加上<T>,这是为了区分泛型T和普通重名类T
    //sql不能在方法里写好,只能作为参数传入
    //sql预编译完全可以根据输出的javaBean使用反射写好
    public static <T> SinkFunction<T> getClickHouseSink(String sql) {
        return JdbcSink.<T>sink(sql,
                new JdbcStatementBuilder<T>() {
                    @SneakyThrows
                    @Override
                    public void accept(PreparedStatement preparedStatement, T t) throws SQLException {

                        //使用反射提取字段
                        Class<?> clz = t.getClass();
                        Field[] fields = clz.getDeclaredFields();

                        //这里遍历字段是按照JavaBean的字段顺序
                        int offset=0;
                        for (int i = 0; i < fields.length; i++) {
                            Field field = fields[i];
                            field.setAccessible(true);

                            //被注解的字段直接跳过赋值,注意:循环外定义偏移量,遍历到注解字段,增加偏移量,跳过一次循环
                            TransientSink transientSink = field.getAnnotation(TransientSink.class);
                            if (transientSink != null) {
                                offset++;
                                continue;
                            }

                            //获取数据并给占位符赋值
                            Object value = field.get(t);
                            preparedStatement.setObject(i-offset+1,value);
                        }
                    }
                },new JdbcExecutionOptions.Builder()//执行参数,批量提交
                        .withBatchIntervalMs(1000L)
                        .withBatchSize(5)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withDriverName(GmallConfig.CLICKHOUSE_DRIVER)
                        .withUrl(GmallConfig.CLICKHOUSE_URL)
                        .build()
                );
    }

}
