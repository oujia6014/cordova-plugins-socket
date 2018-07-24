#import "appsocket.h"
//#import "AsyncSocket.h"
#import "GCDAsyncSocket.h"
#import <sys/socket.h>
#import <netinet/in.h>
#import <arpa/inet.h>
#import <unistd.h>
#import "getgateway.h"
#import <ifaddrs.h>
#import <SystemConfiguration/CaptiveNetwork.h>
@interface appsocket ()<GCDAsyncSocketDelegate>
@property (nonatomic, strong) GCDAsyncSocket    *socket;
@property (nonatomic, copy) NSString    *socketCallbckId;
@property(nonatomic,assign)int countDown; // 倒数计时用
@property(nonatomic,strong)NSTimer *timer; // timer
@property(nonatomic,strong)NSMutableData *completeData;//接收完整的包
//完整包的长度
@property(nonatomic,assign)NSInteger lenght;
//接收成功的回调
@property(nonatomic,copy) recive_Success_Block reciveBlock;
@end
static int const tick = 180;
@implementation appsocket
//链接socket
-(void) connecting : (CDVInvokedUrlCommand *) command{
    self.socketCallbckId = command.callbackId;
    self.socket=  [[GCDAsyncSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];
    NSError *error = nil;
    int socketPort= [[command.arguments objectAtIndex:0] intValue];
    NSString *hostIP = [command.arguments objectAtIndex:1];;
    [self.socket connectToHost:hostIP onPort:socketPort viaInterface:nil withTimeout:-1 error:&error];
}

//发送消息
-(void) sendMsg : (CDVInvokedUrlCommand *) command{
    NSString* value = @"sendMsg";
    NSArray *message = [command.arguments objectAtIndex:0];
    [self.socket writeData:message  withTimeout:1 tag:1];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:value];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}



//字符串转16进制
-(NSString *)stringWithHexNumber:(NSUInteger)hexNumber{
    char hexChar[6];
    sprintf(hexChar, "%x", (int)hexNumber);
    NSString *hexString = [NSString stringWithCString:hexChar encoding:NSUTF8StringEncoding];
    return hexString;
}
//16转字符串
- (NSNumber *) numberHexString:(NSString *)aHexString{
    if (nil == aHexString){return nil;}
    NSScanner * scanner = [NSScanner scannerWithString:aHexString];
    unsigned long long longlongValue;
    [scanner scanHexLongLong:&longlongValue];
    NSNumber * hexNumber = [NSNumber numberWithLongLong:longlongValue];
    return hexNumber;
}


//关闭
-(void) close : (CDVInvokedUrlCommand *) command{
    [self.socket disconnect];
    [self stopTimer];
    NSString* value = @"关闭";
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:value];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

//获取sssid
-(void) getSSID : (CDVInvokedUrlCommand *) command{
    NSArray *value = [[self getSSIDInfo] objectForKey:@"SSID"];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:value];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

//接收socket返回信息并返回
- (void)socket:(GCDAsyncSocket *)sock didReadData:(NSData *)data withTag:(long)tag{
    Byte *bytes = (Byte *)[data bytes];
    NSString *content=@"";
    for(int i=0;i<[data length];i++){
        NSString *newHexStr = [NSString stringWithFormat:@"%x",bytes[i]&0xff];
        if([newHexStr length]==1){
            content = [NSString stringWithFormat:@"%@0%@ ",content,newHexStr];
        } else {
            content = [NSString stringWithFormat:@"%@%@ ",content,newHexStr];}
    }
    [self.socket  readDataWithTimeout:-1 tag:0];
    NSString* receiveHook = [NSString stringWithFormat:@"javascript:window.cordova.plugins.appsocket.receive(\"%@\")",content];
    [self.commandDelegate evalJs:receiveHook];
    [self stopTimer];
    [self startCountDown];
}


//socket链接成功回调
- (void)socket:(GCDAsyncSocket *)sock didConnectToHost:(NSString *)host port:(uint16_t)port{
    [self stopTimer];
    [self startCountDown];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"链接成功"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.socketCallbckId ];
    [self.socket  readDataWithTimeout:-1 tag:0];
}

//socket链接失败回调
- (void)socketDidDisconnect:(GCDAsyncSocket *)sock withError:(NSError *)err{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"链接失败"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.socketCallbckId ];
    [self.socket disconnect];
    return;
}

//获取网关地址
- (NSString *) localIPAddress{
    NSString *address = @"error";
    struct ifaddrs *interfaces = NULL;
    struct ifaddrs *temp_addr = NULL;
    int success = 0;
    success = getifaddrs(&interfaces);
    if (success == 0) {
        temp_addr = interfaces;
        while(temp_addr != NULL) {
            if(temp_addr->ifa_addr->sa_family == AF_INET) {
                if([[NSString stringWithUTF8String:temp_addr->ifa_name] isEqualToString:@"en0"]){
                    address = [NSString stringWithUTF8String:inet_ntoa(((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr)];
                }}temp_addr = temp_addr->ifa_next;}
    }
    freeifaddrs(interfaces);
    in_addr_t i = inet_addr([address cStringUsingEncoding:NSUTF8StringEncoding]);
    in_addr_t* x = &i;
    unsigned char *s = getdefaultgateway(x);
    NSString *ip=[NSString stringWithFormat:@"%d.%d.%d.%d",s[0],s[1],s[2],s[3]];
    free(s);
    return ip;
}

//接收数据
- (void) reciveData:(recive_Success_Block)reciveBlock {
    _reciveBlock = reciveBlock;
}


//开启倒计时
-(void)startCountDown {
    _countDown = tick;
    _timer = [NSTimer timerWithTimeInterval:1.0 target:self selector:@selector(timerFired:) userInfo:nil repeats:YES];
    [[NSRunLoop currentRunLoop] addTimer:_timer forMode:NSRunLoopCommonModes];
}

//倒计时处理
-(void)timerFired:(NSTimer *)timer {
    switch (_countDown) {
        case 1:
            [self stopTimer];
            [self sendCloseMessage];
            break;
        default:
            _countDown -=1;
            break;
    }
}

//发送socket超时断开信息
-(void)sendCloseMessage{
    NSString *content = @"socket已断开";
    NSString* receiveHook = [NSString stringWithFormat:@"javascript:window.cordova.plugins.appsocket.receive(\"%@\")",content];
    [self.commandDelegate evalJs:receiveHook];
}

//销毁定时器
- (void)stopTimer {
    if (_timer) {
        [_timer invalidate];
    }
}
//获取ssid
- (id)getSSIDInfo {
    NSArray *ifs = (__bridge_transfer id)CNCopySupportedInterfaces();
    id info = nil;
    for (NSString *ifnam in ifs) {
        info = (__bridge_transfer id)CNCopyCurrentNetworkInfo((__bridge CFStringRef)ifnam);
        if (info && [info count]) { break; }
    }
    return info;
}
@end

