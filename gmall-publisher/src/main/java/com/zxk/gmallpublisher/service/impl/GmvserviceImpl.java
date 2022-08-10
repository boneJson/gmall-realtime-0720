package com.zxk.gmallpublisher.service.impl;

import com.zxk.gmallpublisher.mapper.GmvMapper;
import com.zxk.gmallpublisher.service.GmvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
@Service
public class GmvserviceImpl implements GmvService {

    @Autowired//自动注入
    private GmvMapper gmvMapper;//该接口的实现类交给mybatis,设置里校验bean的报错改为警告

    @Override
    public Double getGmv(int date) {
        return gmvMapper.selectGmv(date);
    }
}
