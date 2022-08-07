package com.zxk.gmall.realtime.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)//定义注解仅加在字段上
@Retention(RetentionPolicy.RUNTIME)//运行时生效
public @interface TransientSink {
}
