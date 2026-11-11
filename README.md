# OXENDORAX 1000 Android starter

## Build
This project is configured for Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 17 and Android SDK 35.
The GitHub Actions workflow builds `app-debug.apk` in the cloud.

## Bluetooth protocol expected by this starter
Phone -> Arduino: `CALIBRATE`, `START`, `STOP` (each terminated by newline).
Arduino -> phone: `READY`, `CAL_PROGRESS:0..100`, `CAL_DONE`, `SIGNAL:<number>`, `ERROR:<message>`.

Pair the HC-05 from Android Bluetooth settings before connecting in the app.
This starter connects only to already-paired classic Bluetooth devices using SPP.

## Important
This is a starter app, not validated detector firmware. The app displays signal values sent by the Arduino.
Real metal detection, gold identification and ground balancing depend on the sensor circuit and Arduino firmware.
The calibration progress is shown only when received from the Arduino; the app does not fake completion.
