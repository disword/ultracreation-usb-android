package com.ultracreation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

/**
 * Created by disword on 16/11/24.
 */
public class UsbRequestReceiver extends BroadcastReceiver {
    public static final String USB_PERMISSION = "com.ultracreation.USB_PERMISSION";
    private final String TAG = UsbRequestReceiver.class.getSimpleName();
    private CallbackContext requestCallback;
    private CallbackContext usbCallback;
    private UsbCallBack mUsbCallBack;
    public interface UsbCallBack{
        void attach();
        void detach();
    }
    public UsbRequestReceiver() {

    }


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        System.out.println("action = " + action);
        if (USB_PERMISSION.equals(action)) {
            // deal with the user answer about the permission
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.d(TAG, "Permission to connect to the device was accepted!");
                if (requestCallback != null) {
                    String msg = "Permission to connect to the device was accepted!";
                    PluginResult result = new PluginResult(PluginResult.Status.OK, msg);
                    result.setKeepCallback(true);
                    requestCallback.sendPluginResult(result);
                }
            } else {
                Log.d(TAG, "Permission to connect to the device was denied!");
                if (requestCallback != null) {
                    String msg = "Permission to connect to the device was denied!";
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, msg);
                    result.setKeepCallback(true);
                    requestCallback.sendPluginResult(result);
                }
            }
            // unregister the broadcast receiver since it's no longer needed
        } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            System.out.println("usbCallback = " + usbCallback);
            if (usbCallback != null) {
                String msg = "Usb Attach";
                PluginResult result = new PluginResult(PluginResult.Status.OK, msg);
                result.setKeepCallback(true);
                usbCallback.sendPluginResult(result);
            }
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            if (usbCallback != null) {
                String msg = "USB detach.";
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, msg);
                result.setKeepCallback(true);
                usbCallback.sendPluginResult(result);
            }
        }
    }

    public void setRequestCallback(CallbackContext requestCallback) {
        this.requestCallback = requestCallback;
    }

    public void setUsbCallback(CallbackContext usbCallback) {
        this.usbCallback = usbCallback;
    }
}
