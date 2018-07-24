package com.vanward.appsocket;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by suboan on 2017/10/23.
 */
public class appsocket extends CordovaPlugin {
    
    public Socket socket = null;
    // 该线程处理Socket所对用的输入输出流
    public BufferedReader br = null;
    public OutputStream os = null;
    public PrintWriter pw = null;
    
    public Timer checkSocketTimerOut = null;//检查socket返回超时定时器
    public int socketTimerOutSec = 180;//socket返回超时(秒)
    
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("connecting")) {
            int port = args.getInt(0);
            String ip = args.getString(1);
            this.connectSocket(ip,port,callbackContext);
            return true;
        }
        else if(action.equals("sendMsg")) {
            String message = args.getString(0);
            this.sendMsg(message,callbackContext);
            return true;
        }
        else if(action.equals("close")) {
            if(socket != null) {
                try {
                    socket.close();
                    socket = null;
                    
                    //取消检查socket超时定时器
                    if (checkSocketTimerOut != null){
                        checkSocketTimerOut.cancel();
                        checkSocketTimerOut = null;
                    }
                    callbackContext.success();
                } catch (IOException e) {
                    e.printStackTrace();
                    callbackContext.error("关闭socket失败");
                }
            }
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
                    //获取网关地址
                    socket = new Socket();
                    SocketAddress socAddress = new InetSocketAddress(ip, port);
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
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    new Thread(new onMessage()).start();//接收服务器信息
                    checkSocketStatus();//检查socket状态
                    
                    os = socket.getOutputStream();
                    pw = new PrintWriter(os);
                    
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
    private void sendMsg(final String message, final CallbackContext callbackContext){
        if (socket == null){
            callbackContext.error("发送数据失败");
            return;
        }
        pw.write(message);
        pw.flush();
        callbackContext.success();
    }
    //接收服务器信息
    private class onMessage implements Runnable {
        
        @Override
        public void run() {
            String content = null;
            // 不断的读取Socket输入流的内容
            while (true) {
                //关闭socket之后退出循环
                if (socket == null)
                    break;
                try {
                    if ((content = br.readLine()) != null){
                        socketTimerOutSec = 180;//重置超时时间
                        //向js发送信息
                        final String receiveHook = "javascript:window.cordova.plugins.appsocket.receive(\"" + content + "\");";
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.loadUrl(receiveHook);
                            }
                        });
                    }
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
    }
    //检查socket状态
    private void checkSocketStatus() {
        if (checkSocketTimerOut != null)
            return;
        checkSocketTimerOut = new Timer();
        checkSocketTimerOut.schedule(new TimerTask() {
            @Override
            public void run() {
                socketTimerOutSec--;//倒数
                if (socketTimerOutSec < 0 ) {
                    socketTimerOutSec = 0;
                    try {
                        if(socket != null){
                            socket.close();
                            socket = null;
                            checkSocketTimerOut.cancel();
                            checkSocketTimerOut = null;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //向js发送信息
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.loadUrl("javascript:window.cordova.plugins.appsocket.receive(\"socket已断开\");");
                        }
                    });
                    
                }
            }
        }, 0, 1000);
    }
}
