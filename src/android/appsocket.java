package com.vanward.appsocket;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.TimerTask;

public class appsocket extends CordovaPlugin {

    public Socket socket = null;
    // 该线程处理Socket所对用的输入输出流
    public BufferedReader br = null;
    public OutputStream os = null;
    public PrintWriter pw = null;
    public InputStream is = null;//字节输入流

    public Timer checkSocketTimerOut = null;//检查socket返回超时定时器
    public int socketTimerOutSec = 180;//socket返回超时(秒)

    public String returnHeader = null;//上报数据包帧头
    public int returnBytes = 0;//上报数据包字节总长
    public int sendBytes = 0;//下发数据包字节总长
    public String sendVerify = null;//下发数据包验证方式

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("connecting")) {
            int port = args.getInt(0);
            JSONObject jsonObject = args.getJSONObject(1);
            String Header = jsonObject.getString("return_header");
            returnHeader = Integer.toHexString(Integer.parseInt(Header.split(",")[0]) & 0xFF) + " " + Integer.toHexString(Integer.parseInt(Header.split(",")[1]) & 0xFF);
            returnBytes = jsonObject.getInt("return_bytes");
            sendBytes = jsonObject.getInt("send_bytes");
            sendVerify = jsonObject.getString("send_verify");
            this.connectSocket(null,port,callbackContext);
            return true;
        }
    
        else if(action.equals("sendMsg")) {
            JSONArray bytesList = args.getJSONArray(0);
            this.sendByte(bytesList,callbackContext);
            return true;
        }
        else if(action.equals("close")) {
            try {
                closeSocketConn();
                callbackContext.success();
            } catch (IOException e) {
                e.printStackTrace();
                callbackContext.error("关闭socket失败");
            }
            return true;
        }
        else if(action.equals("getSSID")) {
            this.getSSID(callbackContext);
            return true;
        }
        return false;
    }

    //字节转IP地址格式
    private String intToIp(int paramInt) {
        return (paramInt & 0xFF) + "." + (0xFF & paramInt >> 8) + "." + (0xFF & paramInt >> 16) + "."
                + (0xFF & paramInt >> 24);
    }

    //连接socket
    private void connectSocket(final String ip,final int port, final CallbackContext callbackContext) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String connectIP = ip;
                    if (connectIP == null){
                        //获取网关地址
                        WifiManager my_wifiManager = ((WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE));
                        DhcpInfo dhcpInfo = my_wifiManager.getDhcpInfo();
                        connectIP = intToIp(dhcpInfo.gateway);
                    }

                    socket = new Socket();
                    SocketAddress socAddress = new InetSocketAddress(connectIP, port);
                    socket.connect(socAddress, 5000);
                    int k = 0;
                    while(!socket.isConnected() && !socket.isBound()) {
                        Thread.sleep(100);
                        k++;
                        if (k >= 200) {
                            callbackContext.error("连接socket超时");
                            break;
                        }
                    }
                    socket.setKeepAlive(true);
                    //得到socket读写流
                    os = socket.getOutputStream();
                    pw = new PrintWriter(os);
                    //得到socket输入流
                    is = socket.getInputStream();
                    br = new BufferedReader(new InputStreamReader(is));

                    new Thread(new onMessage()).start();//接收服务器信息
                    checkSocketStatus();//检查socket状态

                    callbackContext.success();
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackContext.error("连接socket失败");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    callbackContext.error("连接socket失败");
                }
            }
        }).start();
    }

    //发送信息
    private void sendJson(final String message, final CallbackContext callbackContext){
        if (socket == null){
            callbackContext.error("发送数据失败");
            return;
        }
        //发送字符串
        pw.write(message);
        pw.flush();
        callbackContext.success();
    }

    //发送字节
    private void sendByte(byte[] bytes, final CallbackContext callbackContext){
        if (socket == null){
            callbackContext.error("发送数据失败");
            return;
        }
        //发送byte[]字节
        try {
            os.write(bytes);
            os.flush();
            callbackContext.success();
        } catch (IOException e) {
            e.printStackTrace();
            callbackContext.error("发送数据失败");
        }
    }

    //接收服务器信息
    private class onMessage implements Runnable {
        @Override
        public void run() {
            String content = null;
            int count;
            // 不断的读取Socket输入流的内容
            while (true) {
                //关闭socket之后退出循环
                if (socket == null)
                    break;
                //接收byte[]字节
                try {
                    byte[] bytes = new byte[returnBytes];
                    if ((count = is.read(bytes)) > 0){
                        socketTimerOutSec = 180;//重置超时时间
                        
                        final String receiveHook = "javascript:window.cordova.plugins.appsocket.receive(\"" + bytes + "\");";
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.loadUrl(receiveHook);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //byte[]转16进制字符串
    private static String byte2hex(byte [] buffer,int count){
        String hex = "";
        for(int i = 0; i < count; i++){
            String temp = Integer.toHexString(buffer[i] & 0xFF);
            if(temp.length() == 1){
                temp = "0" + temp;
            }
            hex = hex + temp + " ";
        }
        return hex.substring(0,hex.length()-1);
    }

    //检查socket状态
    private void checkSocketStatus() {
        if (checkSocketTimerOut != null)
            return;
        socketTimerOutSec = 180;//重置时间
        checkSocketTimerOut = new Timer();
        checkSocketTimerOut.schedule(new TimerTask() {
            @Override
            public void run() {
                socketTimerOutSec--;//倒数
                if (socketTimerOutSec < 0 ) {
                    socketTimerOutSec = 0;
                    try {
                        closeSocketConn();
                        //向js发送信息
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.loadUrl("javascript:window.cordova.plugins.appsocket.receive(\"socket已断开\");");
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 0, 1000);
    }

    //断开socket连接
    private void closeSocketConn() throws IOException {
        if(socket != null) {
            //关闭资源
            br.close();
            is.close();
            pw.close();
            os.close();
            socket.close();
            socket = null;
            //取消检查socket超时定时器
            if (checkSocketTimerOut != null){
                checkSocketTimerOut.cancel();
                checkSocketTimerOut = null;
            }
        }
    }

    //获取WIFI名字
    private void getSSID(final CallbackContext callbackContext){
        WifiManager wifiManager = (WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID().replace("\"", "");
        if (ssid != null) {
            callbackContext.success(ssid);
        } else {
            callbackContext.error("获取SSID时出现错误");
        }
    }
}
