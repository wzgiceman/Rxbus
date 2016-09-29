package com.wzgiceman.event;

/**
 * 修改文字事件
 * Created by WZG on 2016/9/29.
 */

public class EventChangeText {
    private String changeText;

    public EventChangeText(String changeText) {
        this.changeText = changeText;
    }

    public String getChangeText() {
        return changeText;
    }

    public void setChangeText(String changeText) {
        this.changeText = changeText;
    }
}
