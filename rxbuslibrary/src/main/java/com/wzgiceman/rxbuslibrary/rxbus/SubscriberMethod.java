package com.wzgiceman.rxbuslibrary.rxbus;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 数据类
 * @author wzg 2016/9/21
 */
public class SubscriberMethod {
    public Method method;
    public ThreadMode threadMode;
    public Class<?> eventType;
    public Object subscriber;
    public int code;
    public boolean sticky;

    public SubscriberMethod(Object subscriber, Method method, Class<?> eventType, int code,ThreadMode threadMode,boolean sticky ) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.subscriber = subscriber;
        this.code = code;
        this.sticky=sticky;
    }


    /**
     * 调用方法
     * @param o 参数
     */
    public void invoke(Object o){
        try {
            method.invoke(subscriber, o);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}
