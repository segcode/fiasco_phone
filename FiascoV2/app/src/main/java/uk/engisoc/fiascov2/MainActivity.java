package uk.engisoc.fiascov2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

class SynchronizedData {
    private Location loc;

    private float lastPressure;
    private float lastTemp;
    private float lastGravity;

    private Boolean providerEnabled;

    SynchronizedData(Location loc) {
        this.loc = loc;
        lastPressure = 0;
        lastTemp = 0;
        lastGravity = 0;
        providerEnabled = true;
    }

    synchronized  void setPressure(float pressure){
        lastPressure = pressure;
    }

    synchronized void setTemp (float temp){
        lastTemp = temp;
    }

    synchronized void setGravity (float gravity){
        lastGravity = gravity;
    }

    synchronized void setLoc(Location newLoc) {
        loc = newLoc;
    }

    // Creates an SMS message containing the information from this location.
    synchronized String getLocString() {
        String MSG_TERMINATOR = ";";
        String DATA_SEPARATOR = ",";

        if (loc == null) {
            return System.currentTimeMillis() + DATA_SEPARATOR + "No Location!" + MSG_TERMINATOR;
        }

        return String.valueOf(System.currentTimeMillis()) +
                DATA_SEPARATOR +
                loc.getLongitude() +
                DATA_SEPARATOR +
                loc.getLatitude() +
                DATA_SEPARATOR +
                loc.getAltitude() +
                DATA_SEPARATOR +
                loc.getSpeed() +
                DATA_SEPARATOR +
                loc.getBearing() +
                DATA_SEPARATOR +
                loc.getAccuracy() +
                DATA_SEPARATOR +
                loc.getTime() +
                DATA_SEPARATOR +
                providerEnabled.toString() +
                DATA_SEPARATOR +
                lastTemp +
                DATA_SEPARATOR +
                lastGravity +
                DATA_SEPARATOR +
                lastPressure +
                MSG_TERMINATOR;
    }

    synchronized void setProviderEnabled(boolean val) {
        this.providerEnabled = val;
    }
}

public class MainActivity extends AppCompatActivity implements LocationListener{

    private final static String[] destinationAddresses = {"07473424585", "07591849894"};

    private final static boolean RUN_AS_DEMON = true;
    private final static String SMS_THREAD_NAME = "smsThread";

    private final static long INITIAL_DELAY = 0L;
    private final static long SEND_PERIOD = 10000L; // 1 Second (1000 ms)

    SynchronizedData current = null;

    private SensorManager enviromentSensorManager;
    private Sensor pressureSensor;
    private Sensor tempSensor;
    private Sensor gravitySensor;

    private SensorEventListener pressureListener = null;
    private SensorEventListener tempListener = null;
    private SensorEventListener gravityListener = null;

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        current = new SynchronizedData(null);

        LocationManager manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            System.out.println("No location permission granted!");
        }

        final long minTime = 500;
        final long minDistance = 0;

        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, this);
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, this);

        enviromentSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (enviromentSensorManager != null){
            pressureSensor = enviromentSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            tempSensor = enviromentSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            gravitySensor = enviromentSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }

        pressureListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                current.setPressure(sensorEvent.values[0]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        gravityListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                current.setGravity(sensorEvent.values[0]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        tempListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                current.setTemp(sensorEvent.values[0]);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        Timer smsTimer = new Timer(SMS_THREAD_NAME, RUN_AS_DEMON);
        smsTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        String coords = getCords();
                        sendSMS(coords);
                    }
                }

                , INITIAL_DELAY, SEND_PERIOD);
    }

    // Called periodically to send an SMS.
    private void sendSMS(String msg) {
        SmsManager smsManager = SmsManager.getDefault();
        for (String destinationAddress : destinationAddresses) {
            smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    msg,
                    null,
                    null
            );
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Gets the current coordinates of the phone.
    private String getCords() {
        return current.getLocString();

    }

    @Override
    public void onLocationChanged(Location location) {
        System.out.println("Location changed!");

        TextView tv = (TextView) findViewById(R.id.statusLabel);
        if (location != null){
            tv.setText("Status: Location acquired!");
        } else {
            tv.setText("Status: No Location!");
        }

        current.setLoc(location);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        System.out.println("Status changed!");
        // TODO
    }

    @Override
    public void onProviderEnabled(String s) {
        System.out.println("Provider Enabled!");
        current.setProviderEnabled(true);
    }

    @Override
    public void onProviderDisabled(String s) {
        System.out.println("Provider Disabled!");
        current.setProviderEnabled(false);
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (pressureListener != null){
            enviromentSensorManager.registerListener(pressureListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (gravityListener != null){
            enviromentSensorManager.registerListener(gravityListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (tempListener != null){
            enviromentSensorManager.registerListener(tempListener, tempSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pressureListener != null){
            enviromentSensorManager.unregisterListener(pressureListener);
        }
        if (gravityListener != null){
            enviromentSensorManager.unregisterListener(gravityListener);
        }
        if (tempListener != null){
            enviromentSensorManager.unregisterListener(tempListener);
        }
    }
}
