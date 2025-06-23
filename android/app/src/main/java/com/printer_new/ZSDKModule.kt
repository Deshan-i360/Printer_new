package com.printer_new

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.zebra.sdk.btleComm.BluetoothLeConnection
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import com.zebra.sdk.printer.discovery.DeviceFilter
import org.json.JSONArray
import org.json.JSONObject

class ZSDKModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val PERMISSION_REQUEST_CODE = 123
    private var discoveryCallback: Callback? = null
    private var isDiscoveryInProgress: Boolean = false

    override fun getName(): String {
        return "ZSDKModule"
    }

    @ReactMethod
    fun zsdkPrinterDiscoveryBluetooth(callback: Callback) {
        discoveryCallback = callback
        
        try {
            Log.d("ZSDKModule", "Starting printer discovery process...")
            
            // Get Bluetooth adapter using BluetoothManager
            val bluetoothManager = reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            
            if (bluetoothAdapter == null) {
                Log.e("ZSDKModule", "Device doesn't support Bluetooth")
                callback.invoke("Device doesn't support Bluetooth", null)
                return
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Log.e("ZSDKModule", "Bluetooth is disabled. Please enable Bluetooth.")
                callback.invoke("Bluetooth is disabled", null)
                return
            }

            // Log Bluetooth state
            Log.d("ZSDKModule", "Bluetooth adapter state: ${getBluetoothStateString(bluetoothAdapter.state)}")
            Log.d("ZSDKModule", "Bluetooth scan mode: ${getBluetoothScanModeString(bluetoothAdapter.scanMode)}")
            
            checkAndRequestPermissions()
            
        } catch (e: Exception) {
            Log.e("ZSDKModule", "Discovery initialization error: ${e.message}", e)
            callback.invoke("Discovery initialization error: ${e.message}", null)
        }
    }

    private fun getBluetoothStateString(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
            else -> "UNKNOWN"
        }
    }

    private fun getBluetoothScanModeString(scanMode: Int): String {
        return when (scanMode) {
            BluetoothAdapter.SCAN_MODE_NONE -> "SCAN_MODE_NONE"
            BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "SCAN_MODE_CONNECTABLE"
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "SCAN_MODE_CONNECTABLE_DISCOVERABLE"
            else -> "UNKNOWN"
        }
    }

    private fun checkAndRequestPermissions() {
        val activity = reactContext.currentActivity as? PermissionAwareActivity
        if (activity == null) {
            discoveryCallback?.invoke("Activity not found", null)
            return
        }

        val permissions = mutableListOf<String>()

        // Add location permissions (required for Bluetooth scanning on Android)
        if (reactContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Add Bluetooth permissions for Android 12 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (reactContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (reactContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.d("ZSDKModule", "Requesting permissions: $permissions")
            activity.requestPermissions(
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE,
                object : PermissionListener {
                    override fun onRequestPermissionsResult(
                        requestCode: Int,
                        permissions: Array<String>,
                        grantResults: IntArray
                    ): Boolean {
                        if (requestCode == PERMISSION_REQUEST_CODE) {
                            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                                startDiscovery()
                            } else {
                                discoveryCallback?.invoke("Required permissions not granted", null)
                            }
                            return true
                        }
                        return false
                    }
                }
            )
        } else {
            // All permissions are already granted
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        if (isDiscoveryInProgress) {
            Log.d("ZSDKModule", "Discovery already in progress, skipping...")
            return
        }
    
        isDiscoveryInProgress = true
        Log.d("ZSDKModule", "Starting Bluetooth printer discovery...")
    
        val deviceFilter = DeviceFilter { device ->
            var des = device.name;
            var devi = device;
            Log.d("ZSDKModule", "Starting Bluetooth printer discovery... $des $device");
            // Customize this filter based on your requirements.
            // Example: Only accept devices with a specific name pattern.
            device.name?.contains("Zebraprinter") == true
        }

        
    
        // // Run discovery in a background thread
        // Thread {
        //     try {
        //         BluetoothDiscoverer.findPrinters(reactContext, object : DiscoveryHandler {
        //             private val foundPrinterList = mutableListOf<Map<String, String>>()
    
        //             override fun foundPrinter(printer: DiscoveredPrinter) {
        //                 Log.d("ZSDKModule", "Raw printer found: ${printer.address}, Class: ${printer.javaClass.simpleName}")
        //                 try {
        //                     val discoveredPrinter = printer as? DiscoveredPrinterBluetooth
        //                     Log.d("ZSDKModule", "Found printer - Address: ${printer.address}, " +
        //                         "Name: ${discoveredPrinter?.friendlyName}, " +
        //                         "Class: ${printer.javaClass.simpleName}")
    
        //                     val foundPrinter = mapOf(
        //                         "address" to printer.address,
        //                         "friendlyName" to (discoveredPrinter?.friendlyName ?: "Unknown"),
        //                         "type" to printer.javaClass.simpleName
        //                     )
        //                     foundPrinterList.add(foundPrinter)
        //                 } catch (e: Exception) {
        //                     Log.e("ZSDKModule", "Error processing found printer: ${e.message}", e)
        //                 }
        //             }
    
        //             override fun discoveryFinished() {
        //                 isDiscoveryInProgress = false
        //                 val jsonArray = JSONArray()
        //                 for (printer in foundPrinterList) {
        //                     jsonArray.put(JSONObject(printer))
        //                 }
        //                 Log.d("ZSDKModule", "Discovery finished. Found ${foundPrinterList.size} printers: $jsonArray")
        //                 discoveryCallback?.invoke(null, jsonArray.toString())
        //             }
    
        //             override fun discoveryError(message: String) {
        //                 isDiscoveryInProgress = false
        //                 Log.e("ZSDKModule", "Discovery error: $message")
        //                 discoveryCallback?.invoke("Discovery error: $message", null)
        //             }
        //         }, deviceFilter) // Pass the device filter
        //     } catch (e: Exception) {
        //         isDiscoveryInProgress = false
        //         Log.e("ZSDKModule", "Error starting discovery: ${e.message}", e)
        //         discoveryCallback?.invoke("Error starting discovery: ${e.message}", null)
        //     }
        // }.start() // Start the thread

        
    }
    

    @ReactMethod
    fun zsdkWriteBluetooth(macAddress: String, zpl: String) {
        Log.d("ZSDKModule", "Going to write via Bluetooth with MAC address: $macAddress and zpl: $zpl")

        var printerConnection: Connection? = null

        try {
            printerConnection = BluetoothConnection(macAddress)
            printerConnection.open()

            if (printerConnection.isConnected) {
                Log.d("ZSDKModule", "Printer connection established.")
                printerConnection.write(zpl.toByteArray())
                Log.d("ZSDKModule", "ZPL sent successfully.")
            }
        } catch (e: Exception) {
            Log.e("ZSDKModule", "Connection error: ${e.message}", e)
        } finally {
            try {
                printerConnection?.close()
                Log.d("ZSDKModule", "Printer connection closed.")
            } catch (ex: ConnectionException) {
                Log.e("ZSDKModule", "Error closing connection: ${ex.message}", ex)
            }
        }
    }
}