# cordova-plugins-socket
将原生socket封装成cordova插件,在webApp中可以使用此插件进行原生socket进行通信


## 链接socket
```
    window.cordova.plugins.appsocket.connecting("端口地址:int", "ip地址:string", () => {
      this.protocolName = this.Property.name;
      this.services.showToast('固件链接成功', 2000);
      console.log('固件链接成功');
    }, () => {
      console.log('固件链接失败');
    });
 ```
## 发送消息
```
    window.cordova.plugins.appsocket.sendMsg('发送的消息', () => {
        console.log('发送消息成功');
    }, (data) => {
        console.log('发送消息失败');
    });
```
## 接收消息
在angular顶部页面添加
```
@Component({
  host: {
    '(document:Socket_RECEIVE_DATA_HOOK)': 'receiveMsg($event)'
  }
})
```
```
  receiveMsg(ev: Event) {
      let message = ev['metadata']['connection']['message'];
      console.log('接收的消息->' + message)
   }
```

## 关闭socket
```
    window.cordova.plugins.appsocket.close(() => {
        console.log('关闭socket成功');
     },(data) => {
        console.error(data);
     });
```

## 获取ssid
```
      window.cordova.plugins.appsocket.getSSID((ssid) => {
          console.log('ssid->' + ssid);
      });
```
