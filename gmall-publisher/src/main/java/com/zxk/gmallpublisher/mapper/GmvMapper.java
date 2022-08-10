package com.zxk.gmallpublisher.mapper;

import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

//这里不加注解就在应用程序上加才能识别
public interface GmvMapper {


    //查询CH,获取GMV总数
    @Select("select sum(order_amount) from dws_trade_province_order_window where toYYYYMMDD(stt)=#{date}")
    public Double selectGmv(int date);//传入查询的日期到sql
}
