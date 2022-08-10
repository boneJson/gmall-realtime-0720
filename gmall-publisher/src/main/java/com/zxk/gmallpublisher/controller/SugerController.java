package com.zxk.gmallpublisher.controller;

import com.zxk.gmallpublisher.service.GmvService;
import com.zxk.gmallpublisher.service.UvService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

//@Controller
@RestController //等价于@Controller的同时所有方法加上@ResponseBody
@RequestMapping("/api/sugar")   //为所有的方法访问映射加上前缀,便于管理
public class SugerController {

    @Autowired
    private GmvService gmvService;

    @Autowired
    private UvService uvService;

    //访问地址为http://localhost:8070/test1
    @RequestMapping("/test1")//方法的映射条件
//    @ResponseBody//可以不返回页面,返回方法的返回值类型

    public String test1() {
        System.out.println("aaaaaaaaa");

        return "{\"id\":\"1001\",\"name\":\"zhangsan\"}";

        //返回的内容为页面才可以
//        return "index.html";
    }


    //访问地址为http://localhost:8070/test2?nn=zhangsan&age=18
    @RequestMapping("/test2")
    //有默认值可传可不传,没有必须传
    public String test2(@RequestParam("nn") String name,@RequestParam(value = "age",defaultValue = "18") String age) {
        System.out.println(name + ":" + age);

        return "success";
    }

    private int getToday() {
        long ts = System.currentTimeMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return Integer.parseInt(sdf.format(ts));
    }

    @RequestMapping("/gmv")
    public String getGmv(@RequestParam(value = "date",defaultValue = "0") int date) {

        //当参数为默认值时,日期赋为今日
        if (date == 0) {
            date = getToday();
        }

        //查询
        Double gmv = gmvService.getGmv(date);


        return "{ " +
                "  \"status\": 0, " +
                "  \"msg\": \"\", " +
                "  \"data\": "+gmv +
                "}";
    }

    @RequestMapping("/ch")
    public String getUvByCh(@RequestParam(value = "date",defaultValue = "0") int date) {

        //当参数为默认值时,日期赋为今日
        if (date == 0) {
            date = getToday();
        }

        //查询
        Map uvByCh = uvService.getUvByCh(date);
        Set chs = uvByCh.keySet();
        Collection uvs = uvByCh.values();




        return "{" +
                "  \"status\": 0," +
                "  \"msg\": \"\"," +
                "  \"data\": {" +
                "    \"categories\": [\"" +
                StringUtils.join(chs, "\",\"") +
                "\"]," +
                "    \"series\": [" +
                "      {" +
                "        \"name\": \"日活\"," +
                "        \"data\": [" +
                StringUtils.join(uvs, ",") +
                "]" +
                "      }" +
                "    ]" +
                "  }" +
                "}";
    }



}
