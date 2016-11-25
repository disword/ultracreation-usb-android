package com.ultracreation;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by disword on 16/11/23.
 */
public class UsbPlugin extends CordovaPlugin {
    private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
    private static final String ACTION_OPEN = "openSerial";
    private static final String ACTION_READ = "readSerial";
    private static final String ACTION_WRITE = "writeSerial";
    private static final String ACTION_WRITE_HEX = "writeSerialHex";
    private static final String ACTION_CLOSE = "closeSerial";
    private static final String ACTION_READ_CALLBACK = "registerReadCallback";
    private static final String ACTION_USB_STATE_CALLBACK = "registerUsbStateCallback";

    private static final String TAG = "UsbPlugin";

    private UsbManager manager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint usbEpIn;
    private UsbEndpoint usbEpOut;
    private UsbRequestReceiver mUsbReceiver;

    protected byte[] mReadBuffer;
    protected byte[] mWriteBuffer;
    protected final Object mReadBufferLock = new Object();
    protected final Object mWriteBufferLock = new Object();
    private boolean register;
    private CallbackContext readCallback;
    private UsbReadTask mUsbReadTask;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        System.out.println("action = " + action);
        JSONObject arg_object = args.optJSONObject(0);
        if (ACTION_REQUEST_PERMISSION.equals(action)) {
            JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
            requestPermission(opts, callbackContext);
            return true;
        } else if (ACTION_USB_STATE_CALLBACK.equals(action)) {
            JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
            registerUsbCallback(opts, callbackContext);
            return true;
        } else if (ACTION_OPEN.equals(action)) {
            JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
            openSerial(opts, callbackContext);
            return true;
        } else if (ACTION_READ_CALLBACK.equals(action)) {
            registerReadCallback(callbackContext);
            return true;
        } else if (ACTION_WRITE.equals(action)) {
            String data = arg_object.getString("data");
            writeSerial(data, callbackContext);
            return true;
        } else if (ACTION_WRITE_HEX.equals(action)) {
            String data = arg_object.getString("data");
            writeSerialHex(data, callbackContext);
            return true;
        } else {
            return false;
        }
    }

    private void requestPermission(final JSONObject opts, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                initManager();
                if (opts.has("vid") && opts.has("pid")) {
                    registerReceiver();
                    setRequestCallBack(callbackContext);
                    Object o_vid = opts.opt("vid"); //can be an integer Number or a hex String
                    Object o_pid = opts.opt("pid"); //can be an integer Number or a hex String
                    int vid = o_vid instanceof Number ? ((Number) o_vid).intValue() : Integer.parseInt((String) o_vid, 16);
                    int pid = o_pid instanceof Number ? ((Number) o_pid).intValue() : Integer.parseInt((String) o_pid, 16);
                    checkDevices(pid, vid, callbackContext);
                } else {
                    callbackContext.error("vid pid not found.");
                }
            }
        });
    }

    private void registerUsbCallback(final JSONObject opts, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                initManager();
                if (opts.has("vid") && opts.has("pid")) {
                    registerReceiver();
                    setUsbCallBack(callbackContext);
                } else {
                    callbackContext.error("vid pid not found.");
                }
            }
        });
    }

    private void openSerial(final JSONObject opts, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (usbDevice != null)
                    setDevice(usbDevice, callbackContext);
                else
                    callbackContext.error("open fail");
            }
        });
    }

    private void registerReadCallback(final CallbackContext callbackContext) {
        Log.d(TAG, "Registering callback");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
//                readCallback = callbackContext;
                if (mUsbReadTask != null)
                    mUsbReadTask.setCallbackContext(callbackContext);
            }
        });
    }

    private void writeSerial(final String data, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (usbEpOut == null) {
                    callbackContext.error("Writing a closed port.");
                } else {
//                    if(!isRegisterRead) {
//                        callbackContext.error("RegisterCallBack is not ready.");
//                        return;
//                    }
                    try {
                        Log.d(TAG, data);
                        byte[] buffer = data.getBytes();
                        write(buffer, 1000);
                        callbackContext.success();
//                        readData();

                    } catch (IOException e) {
                        // deal with error
                        Log.d(TAG, e.getMessage());
                        callbackContext.error(e.getMessage());
                    }
                }
            }
        });
    }


    private void writeSerialHex(final String data, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (usbEpOut == null) {
                    callbackContext.error("Writing a closed port.");
                } else {
                    try {
                        Log.d(TAG, data);
                        byte[] buffer = hexStringToByteArray(data);
                        int result = write(buffer, 1000);
                        callbackContext.success(result + " bytes written.");
//                        readData();
                    } catch (IOException e) {
                        // deal with error
                        Log.d(TAG, e.getMessage());
                        callbackContext.error(e.getMessage());
                    }
                }
            }
        });
    }


    private synchronized void registerReceiver() {
        if (!register) {
            register = true;
            IntentFilter filter = new IntentFilter(UsbRequestReceiver.USB_PERMISSION);
            filter.addAction(UsbRequestReceiver.USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            mUsbReceiver = new UsbRequestReceiver();
            cordova.getActivity().registerReceiver(mUsbReceiver, filter);
        }
    }

    private void setRequestCallBack(CallbackContext callbackContext) {
        mUsbReceiver.setRequestCallback(callbackContext);
    }

    private void setUsbCallBack(CallbackContext callbackContext) {
        mUsbReceiver.setUsbCallback(callbackContext);
    }

    private synchronized void initManager() {
        if (manager == null)
            manager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);
    }


    private void checkDevices(int pid, int vid, CallbackContext callbackContext) {
        boolean isFind = false;
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            System.out.println("device = " + device);
            if (device.getProductId() == pid && device.getVendorId() == vid) {
                usbDevice = device;
                isFind = true;
                break;
            }
            //your code
        }
        if (!isFind)
            callbackContext.error("Device not found.");
        else {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(cordova.getActivity(), 0, new Intent(UsbRequestReceiver.USB_PERMISSION), 0);
            manager.requestPermission(usbDevice, pendingIntent);
        }
    }


    private void setDevice(UsbDevice device, CallbackContext callbackContext) {
        UsbInterface intf = device.getInterface(1);
        usbEpIn = intf.getEndpoint(0);
        usbEpOut = intf.getEndpoint(1);
        int outMax = usbEpOut.getMaxPacketSize();
        int inMax = usbEpIn.getMaxPacketSize();
        mReadBuffer = new byte[inMax];
        mWriteBuffer = new byte[outMax];
        System.out.println("outMax = " + outMax);
        System.out.println("inMax = " + inMax);

        if (device != null) {
            UsbDeviceConnection connection = manager.openDevice(device);
            if (connection != null && connection.claimInterface(intf, true)) {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;
                if (mUsbReadTask != null) {
                    if (!mUsbReadTask.isTheSame(connection, usbEpIn)) {
                        System.out.println("isTheSame");
                        mUsbReadTask.stopTask();
                        mUsbReadTask.interrupt();
                        mUsbReadTask = new UsbReadTask(connection, usbEpIn, cordova.getActivity());
                        mUsbReadTask.start();
                    }
                } else {
                    mUsbReadTask = new UsbReadTask(connection, usbEpIn, cordova.getActivity());
                    mUsbReadTask.start();
                }
                callbackContext.success("open success");
            } else {
                Log.d(TAG, "open FAIL");
                mConnection = null;
                callbackContext.error("open fail");
            }
        }
    }

    private int write(byte[] src, int timeoutMillis) throws IOException {
        int offset = 0;
        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;
                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }
                amtWritten = mConnection.bulkTransfer(usbEpOut, writeBuffer, writeLength,
                        timeoutMillis);
            }
            if (amtWritten <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }
            Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            offset += amtWritten;
        }
        return offset;
    }

    private void readData() {
        byte[] temp = new byte[64];
        int ret = mConnection.bulkTransfer(usbEpIn, temp, temp.length, 3000);
        while (ret > 0) {
            byte[] result = new byte[ret];
            System.arraycopy(temp, 0, result, 0, ret);
            System.out.println("ret = " + ret);
            System.out.println("result = " + new String(result));
            ret = mConnection.bulkTransfer(usbEpIn, temp, temp.length, 3000);
            if (readCallback != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                pluginResult.setKeepCallback(true);
                readCallback.sendPluginResult(pluginResult);
            }
        }
    }


    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
    }

    @Override
    public void onDestroy() {
        ViewGroup container = (ViewGroup) cordova.getActivity().findViewById(android.R.id.content);
        container.removeAllViews();
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView = null;
        }
        super.onDestroy();
        release();
    }

    private void release() {
        if (register && mUsbReceiver != null)
            cordova.getActivity().unregisterReceiver(mUsbReceiver);
        if (mConnection != null)
            mConnection.releaseInterface(usbDevice.getInterface(1));
        if (mUsbReadTask != null) {
            mUsbReadTask.stopTask();
            mUsbReadTask.interrupt();
        }
        manager = null;
        usbDevice = null;
        mConnection = null;
        usbEpIn = null;
        usbEpOut = null;
        mUsbReceiver = null;
        readCallback = null;
        mUsbReadTask = null;


    }
}
