package com.example.minutesperbeat;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.*;
import java.util.stream.Collectors;

import rx.Subscription;



public class MainActivity extends AppCompatActivity {
    private Mds mMds;
    // BleClient singleton
    static private RxBleClient mBleClient;
    static private MdsSubscription memesensor;

    static int musicBpm = 149;

    List<Long> steps = Collections.synchronizedList(new ArrayList<>());

    float calculateBpm() {
        List<Long> considered = steps.stream()
                .filter(step -> step >= System.currentTimeMillis() - 2500)
                .collect(Collectors.toList());

        if (considered.size() < 2) {
            return musicBpm * 0.2f;
        }

        long total = 0;
        for (int i = 0;i < considered.size() - 1; ++i) {
            long step = considered.get(i);
            long next = considered.get(i + 1);
            total += (next - step);
        }
        float avgDurationBetweenSteps = total / (float)(considered.size()-1);
        avgDurationBetweenSteps /= 2; // two feet
        int bpm = (int) (1.0f / (avgDurationBetweenSteps / 1000f / 60f));
        Log.i("bpm", "bpm:" + bpm + " avgdura:" + avgDurationBetweenSteps);
        return bpm;
    }

    private void initMds() {
        mMds = Mds.builder().build(this);
    }

    MediaPlayer mediaPlayer;
    private void playMusic() {
        Random rnd = new Random();
        if(rnd.nextBoolean()){
            mediaPlayer = MediaPlayer.create(this, R.raw.hasselhoff);
            musicBpm = 149;
        }
        else{
            mediaPlayer = MediaPlayer.create(this, R.raw.tapio);
            musicBpm = 130;
        }
        mediaPlayer.start();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                float speed = calculateBpm() / musicBpm;
                speed = Math.min(5, Math.max(0.2f, speed));
                Log.i("FitHub", "speed " + speed);
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            }
        }, 500, 100);
    }

    AccDataResponse.Array previuz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatActivity memethis = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initMds();
        playMusic();
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
                                                        AccDataResponse accResponse = new Gson().fromJson(data, AccDataResponse.class);
                                                        if (accResponse != null && accResponse.body.array.length > 0) {

                                                            AccDataResponse.Array ar = accResponse.body.array[0];
                                                            String accStr =
                                                                    String.format("%.02f, %.02f, %.02f", ar.x, ar.y, ar.z);
                                                            Log.i("memes", accStr);

                                                            if (previuz != null) {
                                                                double totalChange =
                                                                        Math.abs(ar.x - previuz.x) + Math.abs(ar.y - previuz.y) + Math.abs(ar.z - previuz.z);
                                                                if (totalChange > 30) {
                                                                    Log.i("memestepped", "xd " + totalChange);
                                                                    steps.add(System.currentTimeMillis());
                                                                }
                                                            }
                                                            previuz = ar;
                                                        }
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
                                    }
                                });
                                }
                        }
                );
    }

    @Override
    protected void onPause() {
        super.onPause();
        mediaPlayer.stop();
        mediaPlayer.release();
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
