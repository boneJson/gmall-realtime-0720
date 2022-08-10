package com.zxk.gmallpublisher.service.impl;

import com.zxk.gmallpublisher.mapper.UvMapper;
import com.zxk.gmallpublisher.service.UvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UvServiceImpl implements UvService {
    @Autowired
    private UvMapper uvMapper;

    @Override
    public Map getUvByCh(int date) {

        //创建存放结果的集合
        HashMap<String, BigInteger> resultMap = new HashMap<>();

        //查询获取CH数据
        List<Map> mapList = uvMapper.selectUvByCh(date);

        for (Map map : mapList) {
            resultMap.put((String) map.get("ch"), (BigInteger) map.get("uv"));
        }
        return resultMap;
    }
}
