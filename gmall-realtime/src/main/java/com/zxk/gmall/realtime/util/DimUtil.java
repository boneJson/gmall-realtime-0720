package com.zxk.gmall.realtime.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zxk.gmall.realtime.common.GmallConfig;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class DimUtil {

    //注意这里传入的phoenix表名为大写
    public static JSONObject getDimInfo(Connection connection, String tableName, String id) throws Exception {

        //先读旁路缓存
        //查询Redis
        //获取Redis连接
        Jedis jedis = JedisUtil.getJedis();
        String redisKey = "DIM:" + tableName + ":" + id;
        String dimInfoStr = jedis.get(redisKey);
        if (dimInfoStr != null) {
            //重置数据过期时间
            jedis.expire(redisKey, 24 * 60 * 60);

            //归还连接
            jedis.close();

            //返回结果
            return JSON.parseObject(dimInfoStr);
        }

        //拼接SQL
        String querySql = "select * from " + GmallConfig.HBASE_SCHEMA + "." + tableName + " where id='" + id + "'";

        //查询phoenix,不应该传true?
        List<JSONObject> list = JdbcUtil.queryList(connection, querySql, JSONObject.class, false);

        //单条结果
        JSONObject dimInfo = list.get(0);
        //将数据写入Redis
        jedis.set(redisKey, dimInfo.toJSONString());
        //Redis读写都设置过期时间
        jedis.expire(redisKey, 24 * 60 * 60);
        jedis.close();

        return dimInfo;

    }

    //删除缓存中的维表数据,在DimSinkFunction类的写入数据之前,删除缓存
    public static void delDimInfo(String tableName, String id) {
        Jedis jedis = JedisUtil.getJedis();
        String redisKey = "DIM:" + tableName + ":" + id;
        jedis.del(redisKey);
        jedis.close();

    }

    public static void main(String[] args) throws Exception {

        Connection connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);

        JSONObject dimInfo = getDimInfo(connection, "DIM_BASE_TRADEMARK", "12");

        System.out.println(dimInfo);

        connection.close();
    }
}
