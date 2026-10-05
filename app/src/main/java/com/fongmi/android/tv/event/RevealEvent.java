package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

/** 应用配置加载完成后触发，用于各组件的入场动画（与底栏 reveal 同时机）。 */
public final class RevealEvent {

    public static void post() {
        EventBus.getDefault().post(new RevealEvent());
    }
}
