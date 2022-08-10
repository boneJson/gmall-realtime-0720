package com.zxk.gmall.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.zxk.gmall.realtime.common.GmallConfig;
import com.zxk.gmall.realtime.util.DimUtil;
import com.zxk.gmall.realtime.util.MyKafkaUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

public class DimSinkFunction extends RichSinkFunction<JSONObject> {
    private Connection connection;

    @Override
    public void open(Configuration parameters) throws Exception {
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);
    }

    //value：{"sinkTable":"dim_xxx","database":"gmall","table":"base_trademark","type":"insert",
    // "ts":1592270938,"xid":13090,"xoffset":1573,"data":{"id":"12","tm_name":"atguigu"},"old":{}}
    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        PreparedStatement preparedStatement = null;

        try {
            //使用json中的数据和表名拼接sql
            String sinkTable = value.getString("sinkTable");
            JSONObject data = value.getJSONObject("data");
            String upsertSql = genUpsertSql(sinkTable, data);
            System.out.println(upsertSql);

            //预编译
            preparedStatement = connection.prepareStatement(upsertSql);

            //如果更新Phoenix数据,则删除Redis缓存数据
            if ("update".equals(value.getString("type"))) {
                DimUtil.delDimInfo(sinkTable.toUpperCase(),data.getString("id"));
            }

            //执行写入,注意不同于mysql的自动提交事务,phoenix需开启自动提交/手动提交,而且只针对DML语句
            preparedStatement.execute();
            connection.commit();
        } catch (SQLException throwables) {
            //捕捉到异常,插入失败则phoenix中少一条数据,维表中join不上可以去mysql中取,所以不抛而是处理异常(打印异常)不让程序挂掉
            System.out.println("插入数据失败!");
        } finally {
            if (preparedStatement != null) {
                preparedStatement.close();
            }

        }


    }

    /**
     * @param sinkTable tn
     * @param data      {"id":"12","tm_name":"atguigu"}
     * @return upsert into db.tn(id,tm_name,logo_url) values ('12','atguigu','/aaa/bbb')
     */
    private String genUpsertSql(String sinkTable, JSONObject data) {
        Set<String> columns = data.keySet();
        Collection<Object> values = data.values();

        //因为hbase中增改都是put,phoenix对应upsert
        return "upsert into " + GmallConfig.HBASE_SCHEMA + "." + sinkTable + "(" +
                StringUtils.join(columns, ",") + ") values ('" +
                StringUtils.join(values, "','" )+ "')";
    }


}
