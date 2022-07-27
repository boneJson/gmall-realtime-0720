package com.zxk.gmall.realtime.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zxk.gmall.realtime.bean.TableProcess;
import com.zxk.gmall.realtime.common.GmallConfig;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.optimizer.operators.MapDescriptor;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

//广播连接流处理方法
//输出类型使用json,为了减少加工操作
public class MyBroadcastFunction extends BroadcastProcessFunction<JSONObject, String, JSONObject> {
    private Connection connection;
    private MapStateDescriptor<String,TableProcess> stateDescriptor;

    public MyBroadcastFunction(MapStateDescriptor<String,TableProcess> mapStateDescriptor) {
        this.stateDescriptor = mapStateDescriptor;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);
    }

    //Value:{"database":"gmall","table":"cart_info","type":"update","ts":1592270938,"xid":13090,"xoffset":1573,
    // "data":{"id":100924,"user_id":"93","sku_id":16,"cart_price":4488.00,"sku_num":1,
    // "img_url":"http://47.93.148.192:8080/group1/M00/00/02/rBHu8l-sklaALrngAAHGDqdpFtU741.jpg",
    // "sku_name":"华为 HUAWEI P40 麒麟990 5G SoC芯片 5000万超感知徕卡三摄 30倍数字变焦 8GB+128GB亮黑色全网通5G手机",
    // "is_checked":null,"create_time":"2020-06-14 09:28:57","operate_time":null,"is_ordered":1,
    // "order_time":"2021-10-17 09:28:58","source_type":"2401","source_id":null},"old":{"is_ordered":0,"order_time":null}}
    @Override
    public void processElement(JSONObject value, ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {
        //1.获取广播的配置数据
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(stateDescriptor);
        TableProcess tableProcess = broadcastState.get(value.getString("table"));

        String type = value.getString("type");
        //按状态中的配置信息,过滤表,仅写出来源表的数据,以及过滤mxw同步的数据的操作类型为增改
        if (tableProcess != null && ("bootstrap-insert".equals(type) || "insert".equals(type) || "update".equals(type))) {

            //2.根据sinkColumns配置信息 过滤字段
            filter(value.getJSONObject("data"), tableProcess.getSinkColumns());

            //3.补充sinkTable字段写出,为后面数据写入做铺垫,因为json中仅有来源表,数据data,而不知道sink表名,目前无法写出到phoenix
            value.put("sinkTable", tableProcess.getSinkTable());
            out.collect(value);

        } else {
            System.out.println("过滤掉：" + value);
        }
    }

    private void filter(JSONObject data, String sinkColumns) {
        //这里先切割得到所需字段集合再迭代遍历主流数据中的data字段,过滤,
        // 而不使用所需字段对数据的字段名contain防止文本包含现象
        String[] split = sinkColumns.split(",");
        List<String> columnsList = Arrays.asList(split);

//        Iterator<Map.Entry<String, Object>> iterator = data.entrySet().iterator();
//        while (iterator.hasNext()) {
//            Map.Entry<String, Object> next = iterator.next();
                //若字段名不存在,则移除该字段数据
//            if (!columnsList.contains(next.getKey())) {
//                iterator.remove();
//            }
//        }

        data.entrySet().removeIf(next -> !columnsList.contains(next.getKey()));
    }

    //Value:{"before":null,"after":{"source_table":1,"sink_table":"三星","sink_columns":"/static/default.jpg"....},
    // "source":{"version":"1.5.4.Final","connector":"mysql","name":"mysql_binlog_source","ts_ms":1649744439676,
    // "snapshot":"false","db":"gmall-210927-flink","sequence":null,"table":"base_trademark","server_id":0,
    // "gtid":null,"file":"","pos":0,"row":0,"thread":null,"query":null},"op":"r","ts_ms":1649744439678,"transaction":null}
    @Override
    public void processBroadcastElement(String value, Context ctx, Collector<JSONObject> out) throws Exception {
        //1.获取并解析数据为JavaBean对象
        JSONObject jsonObject = JSON.parseObject(value);
        TableProcess tableProcess = JSON.parseObject(jsonObject.getString("after"), TableProcess.class);

        //2.校验表是否存在,如果不存在则建表
        checkTable(tableProcess.getSinkTable(),
                tableProcess.getSinkColumns(),
                tableProcess.getSinkPk(),
                tableProcess.getSinkExtend());

        //3.将数据写入状态
        String key = tableProcess.getSourceTable();
        //这里因为在此之前主类中就已经创建了状态的描述,因此选择使用构造方法传入
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(stateDescriptor);
        //key:sourceTable,value:mysql变化同步的满属性javabean
        broadcastState.put(key, tableProcess);
    }

    //在Phoenix中校验并建表 create table if not exists db.tn(id varchar primary key,name varchar,....) xxx
    //注意:提交sql需要的JDBC流程,类中声明连接对象,并在声明周期方法open中获取phoenix连接,才可以预编译
    //phoenix中的sql默认提交是关闭的,但是DDL不需要,DDL才需要
    private void checkTable(String sinkTable, String sinkColumns, String sinkPk, String sinkExtend) {
        PreparedStatement preparedStatement = null;

        try {
            //处理字段,主键默认为id,扩展字段默认为空串
            if (sinkPk == null || sinkPk.equals("")) {
                sinkPk = "id";
            }
            if (sinkExtend == null) {
                sinkExtend = "";
            }

            StringBuilder sql = new StringBuilder("create table if not exists ")
                    .append(GmallConfig.HBASE_SCHEMA)
                    .append(".")
                    .append(sinkTable)
                    .append("(");

            String[] columns = sinkColumns.split(",");
            for (int i = 0; i < columns.length; i++) {

                //获取字段
                String column = columns[i];

                //判断是否为主键字段
                if (sinkPk.equals(column)) {
                    sql.append(column).append(" varchar primary key");
                } else {
                    sql.append(column).append(" varchar");
                }

                //不是最后一个字段,则添加","
                if (i < columns.length - 1) {
                    sql.append(",");
                }
            }

            sql.append(")").append(sinkExtend);

            System.out.println(sql);

            //预编译SQL
            preparedStatement = connection.prepareStatement(sql.toString());

            //执行
            preparedStatement.execute();

        } catch (SQLException e) {
            //为什么这里捕捉到异常直接抛,因为建表失败必须停止程序,防止数据无法写入
            throw new RuntimeException("建表" + sinkTable + "失败！");
        } finally {
            //资源释放,别关闭连接connection
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
