//
//  NetWork.h
//  万和智能
//
//  Created by vanward on 2017/7/18.
//
//

#import <cordova/CDV.h>

@interface appsocket : CDVPlugin

-(void) connecting : (CDVInvokedUrlCommand *) command;
-(void) sendMsg : (CDVInvokedUrlCommand *) command;
-(void) close : (CDVInvokedUrlCommand *) command;
@end
