package com.example.minutesperbeat;

import android.app.AlertDialog;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.ArrayList;

import rx.Subscription;



public class MainActivity extends AppCompatActivity {
    private Mds mMds;
    // BleClient singleton
    static private RxBleClient mBleClient;
    static private MdsSubscription memesensor;

    // UI
    private ListView mScanResultListView;
    private ArrayList<MyScanResult> mScanResArrayList = new ArrayList<>();
    ArrayAdapter<MyScanResult> mScanResArrayAdapter;

    private void initMds() {
        mMds = Mds.builder().build(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatActivity memethis = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initMds();
        getBleClient().scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {

                            // Process scan result here. filter movesense devices
                            if (scanResult.getBleDevice()!=null &&
                                    scanResult.getBleDevice().getName() != null &&
                                    scanResult.getBleDevice().getName().startsWith("Movesense")) {

                                mMds.connect(scanResult.getBleDevice().getMacAddress(), new MdsConnectionListener() {

                                    @Override
                                    public void onConnect(String s) {
                                    }

                                    @Override
                                    public void onConnectionComplete(String macAddress, String serial) {
                                        String uri = "suunto://" + serial + "/Info";
                                        mMds.get(uri, null, new MdsResponseListener() {
                                            @Override
                                            public void onSuccess(String s) {
                                                Log.i("loggin", "Device " + serial + " /info request succesful: " + s);
                                            }

                                            @Override
                                            public void onError(MdsException e) {
                                                Log.e("loggin", "Device " + serial + " /info returned error: " + e);
                                            }
                                        });

                                        StringBuilder sb = new StringBuilder();
                                        String strContract = sb.append("{\"Uri\": \"").append(serial).append("/Meas/Acc/13").append("\"}").toString();
                                        memesensor = mMds.builder().build(memethis).subscribe("suunto://MDS/EventListener",
                                                strContract, new MdsNotificationListener() {
                                                    @Override
                                                    public void onNotification(String data) {
                                                        Log.i("memes", data);
                                                    }

                                                    @Override
                                                    public void onError(MdsException error) {
                                                        Log.e("meme", "subscription onError(): ", error);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(MdsException e) {
                                        showConnectionError(e);
                                    }

                                    @Override
                                    public void onDisconnect(String bleAddress) {
                                        for (MyScanResult sr : mScanResArrayList) {
                                            if (bleAddress.equals(sr.macAddress))
                                                sr.markDisconnected();
                                        }
                                    }
                                });
                                }
                        }
                );
    }

    private void showConnectionError(MdsException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connection Error:")
                .setMessage(e.getMessage());

        builder.create().show();

    }

    private RxBleClient getBleClient() {
        // Init RxAndroidBle (Ble helper library) if not yet initialized
        if (mBleClient == null)
        {
            mBleClient = RxBleClient.create(this);
        }

        return mBleClient;
    }

}
