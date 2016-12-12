/*global cordova, module*/

module.exports = {
    requestPermission: function(opts, successCallback, errorCallback) {
        if (typeof opts === 'function') {  //user did not pass opts
          errorCallback = successCallback;
          successCallback = opts;
          opts = {};
        }
        cordova.exec(
            successCallback,
            errorCallback,
            'Usb',
            'requestPermission',
            [{'opts': opts}]
        );
    },
    registerUsbStateCallback: function(opts, successCallback, errorCallback) {
            if (typeof opts === 'function') {  //user did not pass opts
              errorCallback = successCallback;
              successCallback = opts;
              opts = {};
            }
            cordova.exec(
                successCallback,
                errorCallback,
                'Usb',
                'registerUsbStateCallback',
                [{'opts': opts}]
            );
    },
    open: function(opts, successCallback, errorCallback) {
            cordova.exec(
                successCallback,
                errorCallback,
                'Usb',
                'openSerial',
                [{'opts': opts}]
            );
    },
    write: function(data, successCallback, errorCallback) {
        cordova.exec(
            successCallback,
            errorCallback,
            'Usb',
            'writeSerial',
            [data]
        );
    },
    registerReadCallback: function(successCallback, errorCallback) {
            cordova.exec(
                successCallback,
                errorCallback,
                'Usb',
                'registerReadCallback',
                []
            );
        }
};
