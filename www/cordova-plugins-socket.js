var exec = require('cordova/exec');

var appSocket = {
    connecting: function(port,initVerify,type, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'appSocket', 'connecting', [port,ip]);
    },
    sendMsg: function(message,successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'appSocket', 'sendMsg', [message]);
    },
    close: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'appSocket', 'close', []);
    },
    getSSID: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'appSocket', 'getSSID', []);
    },
    receive: function(message) {
        var evReceive = document.createEvent('Events');
        // socket返回内容回调
        evReceive.initEvent('Socket_RECEIVE_DATA_HOOK', true, true);
        evReceive.metadata = {
            connection: {
                message: message
            }
        };
        document.dispatchEvent(evReceive);
    }
};

module.exports = appSocket;