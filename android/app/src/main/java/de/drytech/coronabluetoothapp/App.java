package de.drytech.coronabluetoothapp;

import android.content.Context;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class App {

    static RequestQueue requestQueue;

    public static void initialize(Context context) {
        Database.initialize(context);
        requestQueue = Volley.newRequestQueue(context);
    }

    public static RequestQueue getRequestQueue() {
        return requestQueue;
    }

    public static void debugToastShort(Context context, String string) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
        }
    }

    public static void debugToastLong(Context context, String string) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, string, Toast.LENGTH_LONG).show();
        }
    }
}
