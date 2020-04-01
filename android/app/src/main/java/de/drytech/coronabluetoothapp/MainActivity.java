package de.drytech.coronabluetoothapp;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

public class MainActivity extends AppCompatActivity {
    public static final String NAME_PREFERENCES = "DeDrytechCoronaPrefs";

    MainActivityBroadcastReceiver mainActivityBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        App.initialize(this);

        if (BuildConfig.DEBUG) {
            LinearLayout layout = (LinearLayout) findViewById(R.id.layoutMainActivity);

            Button button = new Button(this);
            button.setText("clear database");
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Database database = Database.get();
                    String sql = "DELETE FROM devices;";
                    database.execute(sql);

                    SharedPreferences pref = getSharedPreferences(MainActivity.NAME_PREFERENCES, 0);
                    pref.edit().clear().commit();

                    finish();
                    startActivity(getIntent());
                }
            });
            layout.addView(button);

            LinearLayout layoutDevices = new LinearLayout(this);
            layoutDevices.setOrientation(LinearLayout.VERTICAL);

            Database database = Database.get();
            String sql = "SELECT * FROM devices;";
            Cursor cursor = database.query(sql);
            while (cursor.moveToNext()) {
                TextView textView = new TextView(this);
                textView.setText(cursor.getString(0));
                layoutDevices.addView(textView);
            }
            cursor.close();
            layout.addView(layoutDevices);
        }

        updateTextViewLabelSavedEncountersValue();
        updateTextViewLabelReportedInfectionsValue();

        SharedPreferences pref = getSharedPreferences(MainActivity.NAME_PREFERENCES, 0);
        boolean hasReportedInfection = pref.getBoolean("hasReportedInfection", false);
        if (hasReportedInfection) {
            Button buttonReportInfection = (Button)MainActivity.this.findViewById(R.id.buttonReportInfection);
            buttonReportInfection.setVisibility(View.INVISIBLE);

            TextView textViewLabelHasReportedInfection = (TextView)MainActivity.this.findViewById(R.id.textViewLabelHasReportedInfection);
            textViewLabelHasReportedInfection.setVisibility(View.VISIBLE);
        }

        mainActivityBroadcastReceiver = new MainActivityBroadcastReceiver(this);

        IntentFilter filter = new IntentFilter(ForegroundService.ACTION_NEW_ENCOUNTER);
        registerReceiver(mainActivityBroadcastReceiver, filter);

        filter = new IntentFilter(ForegroundService.ACTION_NEW_INFECTIONS);
        registerReceiver(mainActivityBroadcastReceiver, filter);

        Intent serviceIntent = new Intent(this, ForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBT, 1);
            }
        }


    }

    public void clickButtonReportInfection(final View view) {
        view.setEnabled(false);

        RequestQueue queue = App.getRequestQueue();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        String url = Config.APP_SERVER_URL + "?me=" + bluetoothAdapter.getAddress() + "&isInfected=1";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        App.debugToastLong(MainActivity.this, "response: '" + response + "'");

                        if (response.equals("1")) {
                            SharedPreferences pref = getSharedPreferences(MainActivity.NAME_PREFERENCES, 0);
                            SharedPreferences.Editor editor = pref.edit();
                            editor.putBoolean("hasReportedInfection", true);
                            editor.commit();
                            view.setVisibility(View.INVISIBLE);
                            TextView textViewLabelHasReportedInfection = (TextView)MainActivity.this.findViewById(R.id.textViewLabelHasReportedInfection);
                            textViewLabelHasReportedInfection.setVisibility(View.VISIBLE);
                        } else {
                            //@todo ApplicationError
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        App.debugToastLong(MainActivity.this, error.getMessage());
                        Log.e(error.getMessage(), error.getStackTrace().toString());
                        //@todo ApplicationError
                    }
                }
        );

        queue.add(stringRequest);
    }

    void updateTextViewLabelSavedEncountersValue() {
        Database database = Database.get();
        String sql = "SELECT COUNT(*) AS cnt FROM devices";
        Cursor cursor = database.query(sql);
        cursor.moveToFirst();
        TextView textViewLabelSavedEncountersValue = (TextView)findViewById(R.id.textViewLabelSavedEncountersValue);
        textViewLabelSavedEncountersValue.setText(String.valueOf(cursor.getInt(0)));
        cursor.close();
    }

    void updateTextViewLabelReportedInfectionsValue() {
        SharedPreferences pref = getSharedPreferences(MainActivity.NAME_PREFERENCES, 0);
        TextView textViewLabelReportedInfectionsValue = (TextView)findViewById(R.id.textViewLabelReportedInfectionsValue);
        textViewLabelReportedInfectionsValue.setText(String.valueOf(pref.getInt("numInfections", 0)));
    }
}

class MainActivityBroadcastReceiver extends BroadcastReceiver {
    MainActivity mainActivity;

    public MainActivityBroadcastReceiver(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ForegroundService.ACTION_NEW_ENCOUNTER.equals(action)) {
            mainActivity.updateTextViewLabelSavedEncountersValue();
        } else if (ForegroundService.ACTION_NEW_INFECTIONS.equals(action)) {
            SharedPreferences pref = mainActivity.getSharedPreferences(MainActivity.NAME_PREFERENCES, 0);
            SharedPreferences.Editor editor = pref.edit();
            int numInfections = pref.getInt("numInfections", 0);
            editor.putInt("numInfections", numInfections + intent.getIntExtra("numNewInfections", 0));
            editor.commit();
            mainActivity.updateTextViewLabelReportedInfectionsValue();
        }
    }
}