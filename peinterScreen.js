import React, { useState ,useEffect} from 'react';
import { View, Text, Button, Alert, PermissionsAndroid, Platform } from 'react-native';
import { NativeModules } from 'react-native';

const { ZSDKModule } = NativeModules;

const PrinterScreen = () => {
    const [printers, setPrinters] = useState([]);
    const [macAddress, setMacAddress] = useState('');
    const [zplText, setZplText] = useState('^XA^FO50,50^A0N,50,50^FDHello World^FS^XZ');
    const [loading, setLoading] = useState(false);


    // async function requestPermissions() {
    //     try {
    //         const granted = await PermissionsAndroid.requestMultiple([
    //             PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
    //             PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
    //             PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
    //         ]);

    //         if (
    //             granted["android.permission.BLUETOOTH_SCAN"] === PermissionsAndroid.RESULTS.GRANTED &&
    //             granted["android.permission.BLUETOOTH_CONNECT"] === PermissionsAndroid.RESULTS.GRANTED &&
    //             granted["android.permission.ACCESS_FINE_LOCATION"] === PermissionsAndroid.RESULTS.GRANTED
    //         ) {
    //             console.log("Bluetooth and Location permissions granted");
    //         } else {
    //             console.warn("Permissions not granted");
    //         }
    //     } catch (err) {
    //         console.error(err);
    //     }
    // }

    // requestPermissions();

    async function requestPermissions() {
        try {
            let permissions = [
                PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION, // Required for Bluetooth scanning
            ];
    
            if (Platform.OS === 'android') {
                if (Platform.Version >= 31) {  // Android 12+
                    permissions.push(
                        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
                        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
                    );
                } else {  // Android 11 and below
                    permissions.push(
                        PermissionsAndroid.PERMISSIONS.BLUETOOTH,
                        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
                    );
                }
            }
    
            const granted = await PermissionsAndroid.requestMultiple(permissions);
    
            console.log("Permission results: ", granted);
    
            if (Object.values(granted).every(status => status === PermissionsAndroid.RESULTS.GRANTED)) {
                console.log("All permissions granted.");
            } else {
                console.warn("Some permissions were denied.");
            }
        } catch (err) {
            console.error("Permission request failed", err);
        }
    }
    

    useEffect(() => {
        requestPermissions();
    }, []);


    const discoverPrinters = () => {
        ZSDKModule.zsdkPrinterDiscoveryBluetooth((error, result) => {
            if (error) {
                Alert.alert('Error', `Printer Discovery Failed: ${error}`);
            } else {
                const discoveredPrinters = JSON.parse(result);
                setPrinters(discoveredPrinters);
                Alert.alert('Success', `Found ${discoveredPrinters.length} printer(s)`);
            }
        });
    };

    return (
        <View>
            <Button title="Discover Printers" onPress={discoverPrinters} />
            <Text>Discovered Printers:</Text>
            {printers.map((printer, index) => (
                <Text key={index}>
                    {printer.friendlyName} - {printer.address}
                </Text>
            ))}
        </View>
    );
};

export default PrinterScreen;