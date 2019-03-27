/*
 * *
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.baidu.duer.dcs.sample.sdk.wakeup;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.baidu.duer.dcs.sample.R;
import com.baidu.duer.dcs.sample.sdk.DcsSampleApplication;
import com.baidu.duer.dcs.util.util.NetWorkUtil;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * 唤醒声音处理-唤醒消费者
 * <p>
 * Created by guxiuzhong@baidu.com on 2017/6/22.
 */
public class WakeUpDecodeThread extends Thread {
    private static final String TAG = WakeUpDecodeThread.class.getSimpleName();
    private static Context context;
    // 唤醒成功
    private static final int WAKEUP_SUCCEED = 1;
    // 唤醒词位置
    private int voiceOffset;
    private boolean isWakeUp;
    private WakeUpNative wakeUpNative;
    private Handler handler;
    private volatile boolean isStart = false;
    private LinkedBlockingDeque<byte[]> linkedBlockingDeque;
    private ToastHandle toastHandle;

    public WakeUpDecodeThread(LinkedBlockingDeque<byte[]> linkedBlockingDeque,
                              WakeUpNative wakeUpNative,
                              Handler handler,
                              Context context) {
        this.linkedBlockingDeque = linkedBlockingDeque;
        this.wakeUpNative = wakeUpNative;
        this.handler = handler;
        this.context = context;
        toastHandle = new ToastHandle();
    }


    static class ToastHandle extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Toast.makeText(context, "检查一下网络之后再试试吧", Toast.LENGTH_SHORT).show();
            MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.net_error);
            mediaPlayer.start();
        }
    }


    /**
     * 开始唤醒
     */
    public void startWakeUp() {
        if (isStart) {
            return;
        }
        isStart = true;
        this.start();
    }

    public boolean isStart() {
        return isStart;
    }

    /**
     * 停止唤醒
     */
    public void stopWakeUp() {
        isStart = false;
    }


    /**
     * 1、开启while循环监听声音进行唤醒，不唤醒不跳出循环
     * 2、while循环中不断的从Deque队列获取声音数据，如果大于0，就去执行唤醒（wakeUpDecode()）操作
     * 3、如果返回1，代表唤醒成功，isWakeUp = true ,isStart = false并break跳出循环
     * 4、
     */

    @Override
    public void run() {
        super.run();

        while (isStart) {
            try {
                byte[] data = linkedBlockingDeque.take();
                if (data.length > 0) {
                    boolean isEnd = false;
                    short[] arr = byteArray2ShortArray(data, data.length / 2);
                    int ret = wakeUpNative.wakeUpDecode(arr, arr.length, "", 1, -1, true, voiceOffset++, isEnd);

                    // ret返回1表示唤醒成功，其他表示未唤醒
                    if (ret == WAKEUP_SUCCEED) {

                        if (!NetWorkUtil.isNetworkConnected(DcsSampleApplication.getContext())) {
                            toastHandle.sendEmptyMessage(1);
                            continue;
                        }

                        isWakeUp = true;
                        stopWakeUp();
                        break;
                    }
                    //Log.i("hhh", "ret: " + ret);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (isWakeUp) {
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onWakeUpSucceed();
                    }
                });
            }
        }
        // 重置
        voiceOffset = 0;
        isWakeUp = false;
    }


    /**
     * 将byte数组转为short数组
     *
     * @param data  byte数组的音频数据
     * @param items short数组的大小
     * @return 转换后的short数组
     */
    private short[] byteArray2ShortArray(byte[] data, int items) {
        short[] retVal = new short[items];
        for (int i = 0; i < retVal.length; i++) {
            retVal[i] = (short) ((data[i * 2] & 0xff) | (data[i * 2 + 1] & 0xff) << 8);
        }
        return retVal;
    }


    private IWakeUpListener listener;

    public void setWakeUpListener(IWakeUpListener listener) {
        this.listener = listener;
    }

    /**
     * 唤醒成功回调接口
     */
    public interface IWakeUpListener {
        /**
         * 唤醒成功后回调
         */
        void onWakeUpSucceed();
    }
}