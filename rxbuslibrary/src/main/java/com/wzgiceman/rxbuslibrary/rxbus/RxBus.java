package com.wzgiceman.rxbuslibrary.rxbus;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * RxBus核心处理类
 *
 * @author wzg 2016/9/21
 */
public class RxBus {
    private static volatile RxBus defaultInstance;

    private Map<Class, List<Subscription>> subscriptionsByEventType = new HashMap<>();


    private Map<Object, List<Class>> eventTypesBySubscriber = new HashMap<>();


    private Map<Class, List<SubscriberMethod>> subscriberMethodByEventType = new HashMap<>();

    /*stick数据*/
    private final Map<Class<?>, Object> stickyEvent = new ConcurrentHashMap<>();
    // 主题
    private final Subject bus;

    public RxBus() {
        bus = new SerializedSubject<>(PublishSubject.create());
    }

    // 单例RxBus
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

    /**
     * 提供了一个新的事件,单一类型
     *
     * @param o 事件数据
     */
    public void post(Object o) {
        synchronized (stickyEvent) {
            stickyEvent.put(o.getClass(), o);
        }
        bus.onNext(new Message(-1, o));
    }

    /**
     * 提供了一个新的事件,根据code进行分发
     *
     * @param code 事件code
     * @param o
     */
    public void post(int code, Object o) {
        bus.onNext(new Message(code, o));
    }


    /**
     * 根据传递的code和 eventType 类型返回特定类型(eventType)的 被观察者
     *
     * @param code      事件code
     * @param eventType 事件类型
     * @param <T>
     * @return
     */
    public <T> Observable<T> toObservable(final int code, final Class<T> eventType) {
        return bus.ofType(Message.class)
                .filter(new Func1<Message, Boolean>() {
                    @Override
                    public Boolean call(Message o) {
                        //过滤code和eventType都相同的事件
                        return o.getCode() == code && eventType.isInstance(o.getObject());
                    }
                }).map(new Func1<Message, Object>() {
                    @Override
                    public Object call(Message o) {
                        return o.getObject();
                    }
                }).cast(eventType);
    }


    /**
     * 根据传递的 eventType 类型返回特定类型(eventType)的 被观察者
     */
    public <T> Observable<T> toObservableSticky(final Class<T> eventType) {
        synchronized (stickyEvent) {
            Observable<T> observable = bus.ofType(eventType);
            final Object event = stickyEvent.get(eventType);

            if (event != null) {
                return observable.mergeWith(Observable.create(new Observable.OnSubscribe<T>() {
                    @Override
                    public void call(Subscriber<? super T> subscriber) {
                        subscriber.onNext(eventType.cast(event));
                    }
                }));
            } else {
                return observable;
            }
        }
    }


    /**
     * 注册
     *
     * @param subscriber 订阅者
     */
    public void register(Object subscriber) {
          /*避免重复创建*/
        if (eventTypesBySubscriber.containsKey(subscriber)) {
            return;
        }
        Class<?> subClass = subscriber.getClass();
        Method[] methods = subClass.getDeclaredMethods();
        boolean recive = false;
        for (Method method : methods) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                recive = true;
                //获得参数类型
                Class[] parameterType = method.getParameterTypes();
                //参数不为空 且参数个数为1
                if (parameterType != null && parameterType.length == 1) {

                    Class eventType = parameterType[0];

                    addEventTypeToMap(subscriber, eventType);
                    Subscribe sub = method.getAnnotation(Subscribe.class);
                    int code = sub.code();
                    ThreadMode threadMode = sub.threadMode();
                    boolean sticky = sub.sticky();

                    SubscriberMethod subscriberMethod = new SubscriberMethod(subscriber, method, eventType, code, threadMode,
                            sticky);

                    if (isAdd(eventType, subscriberMethod)) {
                        addSubscriberToMap(eventType, subscriberMethod); 
                    }
                    addSubscriber(subscriberMethod);
                }
            }
        }
        /*没有接受对象，抛出异常*/
        if (!recive) {
            throw new RuntimeException("RxBus error:no recive targert event");
        }
    }


    /**
     * 检查是否已经添加过sub事件
     * @param eventType
     * @param subscriberMethod
     * @return
     */
    private boolean isAdd(Class eventType,SubscriberMethod subscriberMethod){
        boolean resulte=true;
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if(subscriberMethods!=null&&subscriberMethods.size()>0){
            for (SubscriberMethod subscriberMethod1 : subscriberMethods) {
                if(subscriberMethod1.code==subscriberMethod.code){
                    resulte=false;
                    break;
                }
            }
        }
        return resulte;
    }


    /**
     * 将event的类型以订阅中subscriber为key保存到map里
     *
     * @param subscriber 订阅者
     * @param eventType  event类型
     */
    private void addEventTypeToMap(Object subscriber, Class eventType) {
        List<Class> eventTypes = eventTypesBySubscriber.get(subscriber);
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            eventTypesBySubscriber.put(subscriber, eventTypes);
        }

        if (!eventTypes.contains(eventType)) {
            eventTypes.add(eventType);
        }
    }

    /**
     * 将注解方法信息以event类型为key保存到map中
     *
     * @param eventType        event类型
     * @param subscriberMethod 注解方法信息
     */
    private void addSubscriberToMap(Class eventType, SubscriberMethod subscriberMethod) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods == null) {
            subscriberMethods = new ArrayList<>();
            subscriberMethodByEventType.put(eventType, subscriberMethods);
        }

        if (!subscriberMethods.contains(subscriberMethod)) {
            subscriberMethods.add(subscriberMethod);
        }
    }


    /**
     * 将订阅事件以event类型为key保存到map,用于取消订阅时用
     *
     * @param subscriber   subscriber对象类
     * @param subscription 订阅事件
     */
    private void addSubscriptionToMap(Class subscriber, Subscription subscription) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(subscriber);
        if (subscriptions == null) {
            subscriptions = new ArrayList<>();
            subscriptionsByEventType.put(subscriber, subscriptions);
        }

        if (!subscriptions.contains(subscription)) {
            subscriptions.add(subscription);
        }
    }


    /**
     * 用RxJava添加订阅者
     *
     * @param subscriberMethod
     */
    public void addSubscriber(final SubscriberMethod subscriberMethod) {
        Observable observable;
        if (subscriberMethod.sticky) {
            observable = toObservableSticky(subscriberMethod.eventType);
        } else {
            observable = toObservable(subscriberMethod.code, subscriberMethod.eventType);
        }
        Subscription subscription = postToObservable(observable, subscriberMethod)
                .subscribe(new Action1() {
                    @Override
                    public void call(Object o) {
                        callEvent(subscriberMethod.code, o);
                    }
                });
        addSubscriptionToMap(subscriberMethod.subscriber.getClass(), subscription);
    }


    /**
     * 用于处理订阅事件在那个线程中执行
     *
     * @param observable
     * @param subscriberMethod
     * @return
     */
    private Observable postToObservable(Observable observable, SubscriberMethod subscriberMethod) {

        switch (subscriberMethod.threadMode) {
            case MAIN:
                observable.observeOn(AndroidSchedulers.mainThread());
                break;
            case NEW_THREAD:
                observable.observeOn(Schedulers.newThread());
                break;
            case CURRENT_THREAD:
                observable.observeOn(Schedulers.immediate());
                break;
            case IO:
                observable.observeOn(Schedulers.io());
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscriberMethod.threadMode);
        }
        return observable;
    }


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


    /**
     * 取消注册
     *
     * @param subscriber
     */
    public void unRegister(Object subscriber) {
        List<Class> subscribedTypes = eventTypesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unSubscribeBySubscriber(subscriber.getClass());
                unSubscribeMethodByEventType(subscriber, eventType);
            }
            eventTypesBySubscriber.remove(subscriber);
        }
    }


    /**
     * subscriptions unsubscribe
     *
     * @param subscriber
     */
    private void unSubscribeBySubscriber(Class subscriber) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(subscriber);
        if (subscriptions != null) {
            Iterator<Subscription> iterator = subscriptions.iterator();
            while (iterator.hasNext()) {
                Subscription subscription = iterator.next();
                if (subscription != null && !subscription.isUnsubscribed()) {
                    subscription.unsubscribe();
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 移除subscriber对应的subscriberMethods
     *
     * @param subscriber
     * @param eventType
     */
    private void unSubscribeMethodByEventType(Object subscriber, Class eventType) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null && subscriberMethods.size() > 0) {
            Iterator<SubscriberMethod> iterator = subscriberMethods.iterator();
            while (iterator.hasNext()) {
                SubscriberMethod subscriberMethod = iterator.next();
                if (subscriberMethod.subscriber.equals(subscriber)) {
                    iterator.remove();
                }
            }
        }
    }


    /**
     * 移除指定eventType的Sticky事件
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvent) {
            return eventType.cast(stickyEvent.remove(eventType));
        }
    }

    /**
     * 移除所有的Sticky事件
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvent) {
            stickyEvent.clear();
        }
    }

}
