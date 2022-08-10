package com.zxk.gmall.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.zxk.gmall.realtime.common.GmallConfig;
import com.zxk.gmall.realtime.util.DimUtil;
import com.zxk.gmall.realtime.util.ThreadPoolUtil;
import lombok.SneakyThrows;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class DimAsyncFunction<T> extends RichAsyncFunction<T,T> {

    private Connection connection;
    private ThreadPoolExecutor threadPoolExecutor;
    private String tableName;

    public DimAsyncFunction(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);
        threadPoolExecutor = ThreadPoolUtil.getThreadPoolExecutor();
    }

    public abstract String getKey(T input);
    public abstract void join(T input, JSONObject dimInfo);


    @Override
    public void asyncInvoke(T input, ResultFuture<T> resultFuture) {

        threadPoolExecutor.execute(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                //1.查询维表数据,当每张表操作对象都不同或处理逻辑都不同,则考虑传参/抽像方法
                JSONObject dimInfo = DimUtil.getDimInfo(connection, tableName, getKey(input));

                //2.将维表数据补充到JavaBean中
                join(input, dimInfo);

                //3.将补充之后的数据输出
                resultFuture.complete(Collections.singletonList(input));
            }


        });

    }


    @Override
    public void timeout(T input, ResultFuture<T> resultFuture) {
        //再次查询补充信息
        System.out.println("TimeOut:"+input);
    }
}
