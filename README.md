# RxBus

## 背景
仿照EventBus3.0事件传递用法，运用Rxajva实现和EventBus3.0用法完全一样，方便从Eventbus转入到RxBus的使用成本！

## 效果
![这里写图片描述](https://github.com/wzgiceman/Rxbus/blob/master/gif/rxbus_r.gif)


## 依赖


Add it in your root build.gradle at the end of repositories:


```java
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Add the dependency

```java

```




## 代码使用

>RxBus和EventBus3.0的用法完全一样


### 注册-注销-接受事件
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

### 发送消息
```java

    RxBus.getDefault().post(new EventChangeText("我修改了-Main"));

```

### sticky消息的用法

```java
 /*sticky消息*/
    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public  void  event(EventStickText eventStickText){
        tvChange.setText(eventStickText.getMsg());
    }
```
#### 注意

sticky消息在程序销毁的时候要销毁里面的消息

```java

 /*注销所有的sticky消息*/
 RxBus.getDefault().removeAllStickyEvents();

```
#                                     QQ交流群

![](https://github.com/wzgiceman/Rxbus/blob/master/gif/qq.png)








