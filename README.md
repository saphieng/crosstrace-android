# Crosstrace Android
This package enables communication with Crosstrace (xt) tags/devices. The Crosstrace package utilizes McuManager to build packets necessary to communicate with these devices.

## Install

Add JitPack to your root build.gradle at the end of repositories:
```Gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

Add both the McuManager and Crosstrace dependencies to your app build.gradle 
```Gradle
dependencies {
  implementation 'io.runtime.mcumgr:mcumgr-ble:0.11.0'
  implementation 'com.github.saphieng:crosstrace-android:1.0.0'
}
```

## Usage
1. After adding Crosstrace as a dependency, import the package at the top of your Kotlin file `import au.com.saphi.plugins.crosstrace.Crosstrace`
2. Initialize `private var xt = Crosstrace()`
3. Your app needs to filter BLE devices based on the SMP_SERVICE uuid accessible using `xt.SMP_SERVICE` and only connect to these devices.
4. After connecting to a BLE device, invoke `initializeConnection` with, the connected device's name, Activity and Context (crucial step) to start SMP communication
   - This confirms the connection to Crosstrace (xt) specific devices (or those with the same SMP_SERVICE uuid) and starts the McuManager necessary for SMP communication
5. Complete the initialization process 
   - Use the `echo` callback to confirm initialization is a success. 
   - You can pass in any message to indicate success. Eg. "Hello Crosstrace!"
6. After a successful message, start using any of the the `set` commands.
   - These commands help prepare SMP Header packets that are necessary for requesting various information from the devices
7. To obtain a response from a xt tag, the newly built packets need to be sent. This can be done by calling the `retrieve` callback method.

## "Set" Methods
Every `set..` method corresponds to requesting xt device-specific information. These methods use McuManager to **build** header packets.

Depending on the `command` set, `key` passed, or a specific  `set` function called, different headers is sent to the xt tag.

Example calls:
```Kotlin
xt.set(Device.Info)
xt.set("2021-09-08T06:50:40.737715")
xt.set(KeyType.Tek, 1630972800, 5)
```
## "Retrieve" Callback
- In order to obtain a result from xt tags, we need to send across the built packets to the xt device. The `retrieve` callback allows to obtain a response on from the xt tag that we can then use according to our app's needs.

Example use of `retrieve` callback. **Note:** Responses from xt tags are returned as a CBOR map.

```Kotlin
xt.retrieve(object : McuMgrCallback<McuMgrEchoResponse> {
  override fun onResponse(response: McuMgrEchoResponse) {
    // ... do something with response
     val res = CBOR.toStringMap(response.payload) // converts to map to access CBOR fields
  }

  override fun onError(error: McuMgrException) {
    // ... handle error
  }
})
```
## Full Example
Here's a full example of everything put together now that you know how to build packets, send them and do something with the retrieved response.

```Kotlin
// Device Info command sent to build header packet
xt.set(Device.Info)

// Running the `retrieve` callback results in the following response (CBOR map): 
/* {
      rc: <return code, 0=ok>,
      timeStamp: <unix s since 1970...>,
      'tagId': <This tag's unique id>, // eg. 'tagId': 1000012345
      'vbat_mv': <Current measured battery voltage in mV>,
      'chrg': <True if charging>,
      'flash_fill_lvl': <current flash fill level in percent>,
      'num_enc': <number of uncompressed encounters in flash>,
      'num_tek': <number of TEKs (Temporary Exposure Key) in flash>,
      'num_rpi': <number of RPIs (Rolling Proximity Identifier) in flash>,
    } */

// Callback example
xt.retrieve(object : McuMgrCallback<McuMgrEchoResponse> {
  override fun onResponse(response: McuMgrEchoResponse) {
    var tagId = CBOR.toStringMap(response.payload)["tagId"]  // will extract 1000012345 from the response
    print(tagId)
  }

  override fun onError(error: McuMgrException) {
    // ... handle error
  }
})
```

## Notes

The latest built packet will be sent when running the `retrieve` callback, if we run following as such:
```Kotlin
xt.set(Device.Info) // the packet built in this line is overwritten by the next line
xt.set("2021-09-08T06:50:40.737715") 
```

If a specific field value is needed from the CBOR map returned, running the following within a the `retrieve` callback reponse will extract the value from the map.

```Kotlin
CBOR.toStringMap(response.payload)[field] // where <field> refers to a key in the CBOR map
```
