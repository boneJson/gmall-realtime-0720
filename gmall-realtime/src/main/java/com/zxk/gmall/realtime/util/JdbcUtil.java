package com.zxk.gmall.realtime.util;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.CaseFormat;
import com.zxk.gmall.realtime.common.GmallConfig;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcUtil {

    //针对所有JDBC服务的所有查询
    //传入泛型类,为了将一行数据封装成泛型类对象
    public static <T> List<T> queryList(Connection connection,String querySql,Class<T> clz,boolean underScoreToCamel) throws Exception {

        //存放结果的集合
        ArrayList<T> list = new ArrayList<>();

        //预编译
        PreparedStatement preparedStatement = connection.prepareStatement(querySql);

        //执行查询
        ResultSet resultSet = preparedStatement.executeQuery();

        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        //遍历结果集,封装对象放入集合
        while (resultSet.next()) {//行遍历

            //创建T对象
            T t = clz.newInstance();

            //操作一行,列遍历
            for (int i = 0; i < columnCount; i++) {

                //获取列名
                String columnName = metaData.getColumnName(i + 1);
                //获取列值
                Object value = resultSet.getObject(columnName);

                //判断是否需要转换列名信息
                if (underScoreToCamel) {
                    columnName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, columnName.toLowerCase());
                }

                //给T对象赋值
                BeanUtils.setProperty(t,columnName,value);
            }

            //将T对象加入集合
            list.add(t);

        }

        //方法结束前,释放资源
        resultSet.close();
        preparedStatement.close();

        //返回结果
        return list;
    }

    public static void main(String[] args) throws Exception {
        Connection connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);

        System.out.println(queryList(connection, "select * from GMALL2022_REALTIME.DIM_BASE_TRADEMARK"
                , JSONObject.class, true));

        connection.close();
    }
}
