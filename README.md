# mooltifill-android
An autofill service for the mooltipass ble device https://www.mymooltipass.com/. After installation there will be a new autofill service which adds
an autofill option for login forms. When selected, the device is queried for the corresponding
app/service (which has to be confirmed on the mooltipass device).

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/de.mathfactory.mooltifill/)

## Possible issues
### The device does not seem to be accessed
Please:
1. ensure Bluetooth is enabled and the mooltipass ble is powered on
2. ensure the devices are paired
3. use the connection test in the Mooltifill settings, you should obtain "Successfully connected to device!"
### I can't use the soft keyboard when the device is connected via ble
The ble device emulates a keyboard, this may hide the soft keyboard. To always show the keyboard, go to

Settings -> System -> Languages & input -> Physical keyboard -> Show virtual keyboard
