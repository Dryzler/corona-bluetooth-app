package de.drytech.coronabluetoothapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ForegroundService extends Service {
    public static final String ACTION_NEW_ENCOUNTER = "de.drytech.coronabluetoothapp.newencounter";
    public static final String ACTION_NEW_INFECTIONS = "de.drytech.coronabluetoothapp.newinfections";

    public static final String CHANNEL_ID = "DeDrytechCoronaForegroundServiceChannel";

    ForecastServiceBroadcastReceiver mReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).build();
        startForeground(1, notification);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mReceiver = new ForecastServiceBroadcastReceiver();

        IntentFilter filterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filterFound);

        IntentFilter filterStart = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(mReceiver, filterStart);

        IntentFilter filterStop = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filterStop);

        IntentFilter filterState = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filterState);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.startDiscovery();

            RequestQueue queue = App.getRequestQueue();

            String url = Config.APP_SERVER_URL + "?me=" + bluetoothAdapter.getAddress();

            StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            App.debugToastLong(ForegroundService.this, "response: '" + response + "'");

                            if (response.equals("1")) {

                            } else {
                                //@todo ApplicationError
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            App.debugToastLong(ForegroundService.this, error.getMessage());
                            Log.e(error.getMessage(), error.getStackTrace().toString());
                            //@todo ApplicationError
                        }
                    }
            );

            queue.add(stringRequest);
        }

        long millis = 1000 * 60 * 60; //1 hour
        if (BuildConfig.DEBUG) millis = 1000 * 10;
        final long delayMillis = millis;

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                RequestQueue queue = App.getRequestQueue();

                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                String url = Config.APP_SERVER_URL + "?me=" + bluetoothAdapter.getAddress() + "&checkInfected=1";

                StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                try {
                                    JSONObject jsonObj = new JSONObject(response);
                                    JSONArray devices = jsonObj.getJSONArray("devices");
                                    Database database = Database.get();
                                    int numNewInfections = 0;

                                    for (int i = 0; i < devices.length(); i++) {
                                        String device = devices.getString(i);

                                        String sql = "SELECT device FROM devices WHERE device = '" + device + "'";
                                        Cursor result = database.query(sql);
                                        if (result.getCount() == 1) {
                                            numNewInfections++;
                                        }

                                        sql = "SELECT device FROM devices WHERE device = '" + device.replace('-', ':') + "'";
                                        result = database.query(sql);
                                        if (result.getCount() == 1) {
                                            numNewInfections++;
                                        }
                                    }

                                    App.debugToastShort(ForegroundService.this,"numNewInfections=" + numNewInfections);

                                    Intent intentActionNewInfections = new Intent();
                                    intentActionNewInfections.setAction(ForegroundService.ACTION_NEW_INFECTIONS);
                                    intentActionNewInfections.putExtra("numNewInfections", numNewInfections);
                                    ForegroundService.this.sendBroadcast(intentActionNewInfections);

                                } catch (JSONException e) {
                                    //@todo ApplicationError
                                }
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e(error.getMessage(), error.getStackTrace().toString());
                                //@todo ApplicationError
                            }
                        }
                );

                queue.add(stringRequest);

                //your code
                Handler handler = new Handler();
                handler.postDelayed(this, delayMillis);
            }
        }, delayMillis);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}

class ForecastServiceBroadcastReceiver extends BroadcastReceiver {
    public ForecastServiceBroadcastReceiver() {
        super();
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            App.debugToastShort(context, "Device: " + device.getAddress());

            try {
                Database.get().execute("INSERT INTO devices VALUES('" + device.getAddress() + "');");

                Intent intentActionNewEncounter = new Intent();
                intentActionNewEncounter.setAction(ForegroundService.ACTION_NEW_ENCOUNTER);
                context.sendBroadcast(intentActionNewEncounter);

            } catch (SQLiteConstraintException e) {
                //Caused by: android.database.sqlite.SQLiteConstraintException: column device is not unique (code 19)
            }
        } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
            App.debugToastShort(context, "BluetoothAdapter.ACTION_DISCOVERY_STARTED");
        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            App.debugToastShort(context, "BluetoothAdapter.ACTION_DISCOVERY_FINISHED");
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.startDiscovery();
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            App.debugToastShort(context, "BluetoothAdapter.ACTION_STATE_CHANGED");
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.startDiscovery();
        }
    }
}