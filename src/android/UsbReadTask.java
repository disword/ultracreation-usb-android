package com.ultracreation;

import android.app.Activity;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbRequest;
import android.os.SystemClock;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created by disword on 16/11/24.
 */
public class UsbReadTask extends Thread implements Runnable {
    private static final String TAG = "UsbReadTask";
    private boolean stop;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint usbEpIn;
    private CallbackContext callbackContext;
    private int max;
    UsbRequest request;
    private WeakReference<Activity> mOuter;
    public UsbReadTask(UsbDeviceConnection mConnection, UsbEndpoint usbEpIn,Activity activity) {
        this.mConnection = mConnection;
        this.usbEpIn = usbEpIn;
        max = usbEpIn.getMaxPacketSize();
        System.out.println("max = " + max);
        mOuter = new WeakReference<Activity>(activity);
    }

    public void setCallbackContext(CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }

    public void stopTask(){
        stop = true;
        if(request != null) {
            request.cancel();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SystemClock.sleep(100);
                    if(request != null) {
                        request.close();
                        System.out.println("close request");
                    }
                }
            }).start();
        }
        if(mConnection != null) {
            mConnection.close();
        }
    }

    @Override
    public void run() {
        try {
            task();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void task(){
        Activity activity = mOuter.get();
        ByteBuffer buffer = ByteBuffer.allocate(64);
        request = new UsbRequest();
        request.initialize(mConnection, usbEpIn);
        byte status = -1;

        while (true && !stop) {
            System.out.println("run");
            request.queue(buffer, max);
            if (mConnection.requestWait() == request) {
                byte newStatus = buffer.get(0);
                byte[] retData = buffer.array();
                byte[] result = new byte[buffer.position()];
                System.arraycopy(retData,0,result,0,buffer.position());
                System.out.println("size：" + buffer.position());
                System.out.println("收到数据：" + new String(result));

                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);

                if (newStatus != status) {
                    Log.d(TAG, "got status " + newStatus);
                    status = newStatus;
                }

            } else {
                Log.e(TAG, "requestWait failed, exiting");
                stop = true;
                break;
            }
        }
        System.out.println("end");
    }

    public boolean isTheSame(UsbDeviceConnection mConnection, UsbEndpoint usbEpIn){
        return this.mConnection == mConnection && this.usbEpIn == usbEpIn;
    }
}
