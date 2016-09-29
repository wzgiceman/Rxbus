package com.wzgiceman.rxbus;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.wzgiceman.event.EventChangeText;
import com.wzgiceman.rx.RxBus;
import com.wzgiceman.rx.Subscribe;
import com.wzgiceman.rx.ThreadMode;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private TextView tvChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvChange=(TextView)findViewById(R.id.tv_change);
        findViewById(R.id.btn_change_text).setOnClickListener(this);
        findViewById(R.id.btn_code_simple).setOnClickListener(this);
        findViewById(R.id.btn_code_diffrent).setOnClickListener(this);
    }


    @Override
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


}
