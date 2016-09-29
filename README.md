#RxBus

##背景
是否有这样的纠结：已经使用rxjava和rxAndroid到你的项目中，但是项目中又同时存在eventbus；因为rx完全可以替换掉eventbus所以导致了过多引入第三方jar包的问题，对于有代码洁癖和瘦身需求的同学们来说简直是一个噩耗；
如何在最大基础上修改我们已经存在的代码呢，那就是改造一个自己的rxbus，让他使用起来和eventbus一模一样，这样我们只需要将eventbus改名成rxbus即可，其他代码都不需要修改！

废话到此为止，开始我们的优化之路

##项目成果
#### 结果
![这里写图片描述](https://github.com/wzgiceman/Rxbus/blob/master/gif/rxbus-change-text.gif)
####工程目录
![这里写图片描述](https://github.com/wzgiceman/Rxbus/blob/master/gif/6506adc6-d765-406e-be7f-76e56649979b.png)
#### 代码使用
*  注册-注销-接受事件
```java
    /*接受事件*/
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void event(EventChangeText changeText){
        tvChange.setText(changeText.getChangeText());
    }

    @Override
    protected void onStart() {
        super.onStart();
        /*註冊*/
        RxBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /*註銷*/
        RxBus.getDefault().unRegister(this);
    }
```
* 发送消息
```java
 @Override
    public void onClick(View v) {
        switch (v.getId()){
            case  R.id.btn_change_text:
                RxBus.getDefault().post(new EventChangeText("我修改了-Main"));
                break;
        }
    }
```


**用过EventBus的同学一眼就应该能看出，用法完全一模一样**

##封装原理

###封装注解
```java
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    int code() default -1;
    ThreadMode threadMode() default ThreadMode.CURRENT_THREAD;
}
```

暂时咱们先了解ThreadMode 参数，code参数的使用在结尾再给大家解释（比eventbus添加的一个功能）
ThreadMode 指定接受消息的处理所在的线程，我们这里定义了四种情况

###处理模式

```java
public enum ThreadMode {

    /**
     * current thread
     */
    CURRENT_THREAD,

    /**
     * android main thread
     */
    MAIN,


    /**
     * new thread
     */
    NEW_THREAD,

    /**
     * io
     */
    IO

}

```

完全是rx中自带的四种处理模式

###处理信息类

封装处理过程中的相关信息，模式，接收消息对象，code，接受消息类型

```java
public class SubscriberMethod {
    public Method method;
    public ThreadMode threadMode;
    public Class<?> eventType;
    public Object subscriber;
    public int code;

    public SubscriberMethod(Object subscriber, Method method, Class<?> eventType, int code,ThreadMode threadMode) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.subscriber = subscriber;
        this.code = code;
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
```

###RxBus封装
集合上面的类，开始我们的rxbus封装

####初始化单利对象
感兴趣的同学可以查看另一篇关于单利博客[Android-主Activity不一样的单利模式](http://blog.csdn.net/wzgiceman/article/details/51656466)
```java
 private static volatile RxBus defaultInstance;
    public static RxBus getDefault() {
        RxBus rxBus = defaultInstance;
        if (defaultInstance == null) {
            synchronized (RxBus.class) {
                rxBus = defaultInstance;
                if (defaultInstance == null) {
                    rxBus = new RxBus();
                    defaultInstance = rxBus;
                }
            }
        }
        return rxBus;
    }
```

###初始化变量集合
记录注册信息和发布消息信息以及自定义的方法集合

```java

    private Map<Class, List<Subscription>> subscriptionsByEventType = new HashMap<>();


    private Map<Object, List<Class>> eventTypesBySubscriber = new HashMap<>();


    private Map<Class, List<SubscriberMethod>> subscriberMethodByEventType = new HashMap<>();
```

###注册监听
register的时候获取@Subscribe注解的方法的相关信息保存到map,post事件触发的时候调用@Subscribe注解的方法并传入参数. 
```java
    /**
     * 注册
     *
     * @param subscriber 订阅者
     */
    public void register(Object subscriber) {
        Class<?> subClass = subscriber.getClass();
        Method[] methods = subClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                //获得参数类型
                Class[] parameterType = method.getParameterTypes();
                //参数不为空 且参数个数为1
                if (parameterType != null && parameterType.length == 1) {

                    Class eventType = parameterType[0];

                    addEventTypeToMap(subscriber, eventType);
                    Subscribe sub = method.getAnnotation(Subscribe.class);
                    int code = sub.code();
                    ThreadMode threadMode = sub.threadMode();

                    SubscriberMethod subscriberMethod = new SubscriberMethod(subscriber, method, eventType, code, threadMode);
                    addSubscriberToMap(eventType, subscriberMethod);

                    addSubscriber(subscriberMethod);
                }
            }
        }
    }
```
###注销监听
unRegister的移除保存的subscriber、subscriberMethod已经Subscription取消订阅事件
**一定要及时的销毁，不然内存泄露**
```java

    /**
     * 取消注册
     *
     * @param subscriber
     */
    public void unRegister(Object subscriber) {
        List<Class> subscribedTypes = eventTypesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unSubscribeByEventType(eventType);
                unSubscribeMethodByEventType(subscriber, eventType);
            }
            eventTypesBySubscriber.remove(subscriber);
        }
    }


```

###post请求
触发请求

```java
  /**
     * 提供了一个新的事件,单一类型
     *
     * @param o 事件数据
     */
    public void post(Object o) {
        bus.onNext(o);
    }
```
调用rx处理回调
```java

    /**
     * 用RxJava添加订阅者
     *
     * @param subscriberMethod
     */
    public void addSubscriber(final SubscriberMethod subscriberMethod) {
        Observable observable;
        if (subscriberMethod.code == -1) {
            observable = toObservable(subscriberMethod.eventType);
        } else {
            observable = toObservable(subscriberMethod.code, subscriberMethod.eventType);
        }

        Subscription subscription = postToObservable(observable, subscriberMethod)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        callEvent(subscriberMethod.code, o);
                    }
                });
        addSubscriptionToMap(subscriberMethod.eventType, subscription);
    }

```


###code-post请求，更加方便
在借鉴eventbus消息处理的模式上，新加入code判断方式，这样可以更加快速的添加sub对象，不用一个消息初始化一个类，而且可以同时区分一个消息的不同处理方式

**效果**

![这里写图片描述](https://github.com/wzgiceman/Rxbus/blob/master/gif/rxbus_code.gif)

**发送消息**
```java
 public void onClick(View v) {
        switch (v.getId()){
            case  R.id.btn_change_text:
                RxBus.getDefault().post(new EventChangeText("我修改了-Main"));
                break;
            case  R.id.btn_code_simple:
                RxBus.getDefault().post(0x1,"简单的code消息");
                break;
            case  R.id.btn_code_diffrent:
                RxBus.getDefault().post(0x1,new EventChangeText("code方式-我修改了-Main"));
                break;

        }
    }
```
**接收消息**
```java

    /*单一code接受处理*/
    @Subscribe(code = 0x1,threadMode= ThreadMode.MAIN)
    public void event(String changeText){
        tvChange.setText(changeText);
    }


    /*code 不同事件接受處理*/
    @Subscribe(code = 0x1,threadMode= ThreadMode.MAIN)
    public void eventCode(EventChangeText changeText){
        tvChange.setText(changeText.getChangeText());
    }


    /*常規接受事件*/
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void event(EventChangeText changeText){
        tvChange.setText(changeText.getChangeText());
    }
```
看完以后估计大家都明白了使用方法，code实现的过车和ThreadMode实现原理一样，在分发事件处理的时候，通过code的判断达到这样的目的结果

```java
 /**
     * 回调到订阅者的方法中
     *
     * @param code   code
     * @param object obj
     */
    private void callEvent(int code, Object object) {
        Class eventClass = object.getClass();
        List<SubscriberMethod> methods = subscriberMethodByEventType.get(eventClass);
        if (methods != null && methods.size() > 0) {
            for (SubscriberMethod subscriberMethod : methods) {

                Subscribe sub = subscriberMethod.method.getAnnotation(Subscribe.class);
                int c = sub.code();
                if (c == code) {
                    subscriberMethod.invoke(object);
                }

            }
        }
    }
```

##导入
推荐手动导入到自己的工程中，避免多余的第三方jar包导入，只要你的工程中有对rx的支出，将文件copy到工程下面即可：
![这里写图片描述](https://github.com/wzgiceman/Rxbus/blob/master/gif/6506adc6-d765-406e-be7f-76e56649979b.png)

**rx资源地址**
```java
    /*rx-android-java*/
    compile 'io.reactivex:rxjava:+'
    compile 'com.squareup.retrofit:adapter-rxjava:+'
    compile 'com.trello:rxlifecycle:+'
    compile 'com.trello:rxlifecycle-components:+'
```
演示工程中使用的是rx2，可自行替换你使用的版本


