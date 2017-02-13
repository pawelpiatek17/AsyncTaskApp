package com.example.pawe.asynctaskapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.sql.Time;
import java.util.LinkedList;

public class AsyncTaskActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener,SensorEventListener{

    private static final String TAG = "AsyncTaskActivity: ";
    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Location mCurrentLocation;
    float illuminance;
    boolean isLocationSettingsOk;
    protected static final int LOCATION_REQUEST_CODE = 1;
    protected static final int SETTINGS_CHECK_REQUEST_CODE = 2;
    public static final float TWENTY_FIVE_DEGREE_IN_RADIAN = 0.436332313f;
    public static final float ONE_FIFTY_FIVE_DEGREE_IN_RADIAN = 2.7052603f;
    private LinkedList<Float> mCompassHistory = new LinkedList<Float>();
    private float[] mCompassHistorySum = new float[]{0.0f, 0.0f};
    private int mHistoryMaxLength;
    private Marker currentLocationMarker;
    ShowLocationOnMapTask mapLocAsyncTask;
    private SensorManager mSensorManager;
    private Sensor mLight;
    private Sensor mGravity;
    private Sensor mMagneticField;
    private Sensor mGyroscope;
    private float[] mGravityReading;
    private float[] mMagnetometerReading;
    private float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];
    private float mCurrentDegree = 0f;
    private boolean isFlashOn;
    public static Camera camera;
    private long lastGyroscopeUpdateTime;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_async_task);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if( this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            ImageButton imageButtonFlash = (ImageButton) findViewById(R.id.imageButtonLightBulb);
            imageButtonFlash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchFlashlightOnClick(v);
                }
            });
            isFlashOn = false;
            mHistoryMaxLength = 20;
            lastGyroscopeUpdateTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
        createLocationRequest();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        final PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                final LocationSettingsStates r = locationSettingsResult.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS: {
                        isLocationSettingsOk = true;
                        Log.d(TAG,"onResult: result ok");
                        break;
                    }
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            Log.d(TAG,"onResult: result resolution required");
                            status.startResolutionForResult(
                                    AsyncTaskActivity.this,SETTINGS_CHECK_REQUEST_CODE
                            );

                        } catch (IntentSender.SendIntentException e){
                            Log.e(TAG,"onResult: exception " + e.toString());
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE: {
                        Log.d(TAG,"onResult: result change unavailable");
                        break;
                    }
                    default:{
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        try{
            stopLocationUpdates();
        }catch (IllegalStateException e){
            Log.e(TAG,"onStop: exception " + e.toString());

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        try{
            if (currentLocationMarker != null) {
                currentLocationMarker.remove();
            }
            stopLocationUpdates();
        }catch (IllegalStateException e){
            Log.e(TAG,"onStop: exception " + e.toString());
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGravity,
                SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this,mMagneticField,
                SensorManager.SENSOR_DELAY_NORMAL,SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this,mGyroscope,500000000,500000000);
        try{
            startLocationsUpdates();
            if(mapLocAsyncTask != null)
            {
                mapLocAsyncTask.execute();
            }
        }catch (IllegalStateException e){
            Log.e(TAG,"onResume: exception " + e.toString());
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED){
            isLocationSettingsOk = true;
            Log.d(TAG,"onMapReady: permission ok");
        }
        else {
            isLocationSettingsOk = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
            Log.d(TAG,"onMapReady: requestPermissions");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SETTINGS_CHECK_REQUEST_CODE: {
                if(resultCode == RESULT_OK) {
                    isLocationSettingsOk = true;
                    Log.d(TAG,"onActivityResult: ok");
                }
                else {
                    isLocationSettingsOk = false;
                    Log.d(TAG, "onActivityResult: not ok");
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    isLocationSettingsOk = true;
                    Log.d(TAG,"onRequestPermissionsResult: ok");
                }
                else{
                    isLocationSettingsOk = false;
                    Toast.makeText(this, "Permission denied. Location is unavailable",
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "onRequestPermissionsResult: not ok");
                }
            }
        }
    }

    protected void startLocationsUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                    mLocationRequest,this);
        }
    }

    protected void stopLocationUpdates() throws IllegalStateException{
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
        }
    }

    protected void createLocationRequest()
    {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(8000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (isLocationSettingsOk) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(mCurrentLocation != null) {
                LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                MarkerOptions markerO = new MarkerOptions().position(latLng);
                currentLocationMarker = mMap.addMarker(markerO);
                CameraPosition cameraposition = new CameraPosition.Builder()
                        .target(latLng)
                        .zoom(15)
                        .build();
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraposition));
                startLocationsUpdates();
                if(mapLocAsyncTask == null) {
                    Log.d(TAG, "onConnected: create new Async Task");
                    mapLocAsyncTask = new ShowLocationOnMapTask();
                    mapLocAsyncTask.execute();
                }
            }
            startLocationsUpdates();
        }


    }

    private void putMyLocationMarkerOnMap(LatLng latLng){
        if (currentLocationMarker != null) {
            currentLocationMarker.remove();
        }
        currentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng));
        currentLocationMarker.setTitle("Moja lokalizacja");
    }

    public void showMapActivity(View view) {
        Intent intent = new Intent(AsyncTaskActivity.this,MapsActivity.class);
        startActivity(intent);
    }

    public void switchFlashlightOnClick(View view) {
        ImageButton imageButtonFlash = (ImageButton) findViewById(R.id.imageButtonLightBulb);
        if (!isFlashOn) {
            try {
                camera = Camera.open();
                Camera.Parameters p = camera.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(p);
                camera.startPreview();
                imageButtonFlash.setImageResource(R.drawable.light_bulb_256_on);
                isFlashOn = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "switchFlashlightOnClick: exception " + e.toString());
            }
        } else {
            try {
                Camera.Parameters p = camera.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(p);
                camera.stopPreview();
                camera.release();
                imageButtonFlash.setImageResource(R.drawable.light_bulb_256_off);
                isFlashOn = false;
            } catch (RuntimeException e) {
                Log.e(TAG, "switchFlashlightOnClick: exception " + e.toString());
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        try {
            Log.e(TAG,"onLocationChanged: location = " + String.valueOf(location.getLatitude() +
                    " , " + String.valueOf(location.getLongitude())));
            mCurrentLocation = location;
            LatLng latLng;
            latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
            CameraPosition cameraposition = new CameraPosition.Builder()
                    .target(latLng)
                    .zoom(15)
                    .build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraposition));
            putMyLocationMarkerOnMap(latLng);
        }catch (NullPointerException e){
            Log.e(TAG,"onLocationChanged: exception " + e.toString());
    }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mLight) {
            illuminance = event.values[0];
            //Log.d("illum", Float.toString(illuminance));
            setIlluminanceBar(illuminance);
        }
        else if(event.sensor == mGravity) {
            mGravityReading = event.values.clone();
            updateOrientationAngles();
        }
        else if(event.sensor == mMagneticField) {
            mMagnetometerReading = event.values.clone();
            updateOrientationAngles();
        }
        else if(event.sensor == mGyroscope) {
            long eventTime = event.timestamp;
            if (eventTime - lastGyroscopeUpdateTime > 1000000000L) {
                String sensorDataString = "x axis: " + String.valueOf(event.values[0]) +
                        "\n y axis: " + String.valueOf(event.values[1]) +
                        "\n z axis: " + String.valueOf(event.values[2]);
                Log.d(TAG, "onSensorChanged: " + sensorDataString);
                TextView textView = (TextView) findViewById(R.id.textViewGyroscope);
                textView.setText(sensorDataString);
                lastGyroscopeUpdateTime = eventTime;
            }
        }

    }
    private void setIlluminanceBar(float level){
        ImageView ivBar = (ImageView) findViewById(R.id.brightnessBar);
        ImageView ivSun = (ImageView) findViewById(R.id.sun);
        if(level >= 0.0 && level <=2.5){
            ivSun.setImageResource(R.drawable.brightness_low);
            ivBar.setImageResource(R.drawable.bar);
        }
        else if(level > 2.5 && level <=5 ){
            ivSun.setImageResource(R.drawable.brightness_low);
            ivBar.setImageResource(R.drawable.bar_1);
        }
        else if(level > 5 && level <=7.5 ){
            ivSun.setImageResource(R.drawable.brightness_low);
            ivBar.setImageResource(R.drawable.bar_2);
        }
        else if(level > 7.5 && level <=10 ){
            ivSun.setImageResource(R.drawable.brightness_half);
            ivBar.setImageResource(R.drawable.bar_3);
        }
        else if(level > 10 && level <=12.5 ){
            ivSun.setImageResource(R.drawable.brightness_half);
            ivBar.setImageResource(R.drawable.bar_4);
        }
        else if(level > 12.5 && level <=15 ){
            ivSun.setImageResource(R.drawable.brightness_half);
            ivBar.setImageResource(R.drawable.bar_5);
        }
        else if(level > 15 && level <=17.5 ){
            ivSun.setImageResource(R.drawable.brightness_half);
            ivBar.setImageResource(R.drawable.bar_6);
        }
        else if(level > 17.5 && level <=20 ){
            ivSun.setImageResource(R.drawable.brightness_full);
            ivBar.setImageResource(R.drawable.bar_7);
        }
        else if(level > 20){
            ivSun.setImageResource(R.drawable.brightness_full);
            ivBar.setImageResource(R.drawable.bar_8);
        }
    }

    public void updateOrientationAngles() {
        if (mMagnetometerReading != null && mGravityReading != null) {
            SensorManager.getRotationMatrix(mRotationMatrix, null,
                    mGravityReading, mMagnetometerReading);
            float inclination = (float) Math.acos(mRotationMatrix[8]);
            if (inclination < TWENTY_FIVE_DEGREE_IN_RADIAN
                    || inclination > ONE_FIFTY_FIVE_DEGREE_IN_RADIAN)
            {
                SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
                mCompassHistory.add(mOrientationAngles[0]);
                mOrientationAngles[0] = calculateAverageAngle();
            }
            else
            {
                mOrientationAngles[0] = Float.NaN;
                clearCompassHistory();
            }
            float azimuthInDegrees = (float)Math.toDegrees(mOrientationAngles[0]);
            ImageView imageView = (ImageView) findViewById(R.id.compass);
            imageView.setRotation( -azimuthInDegrees);
           // Log.d("compass", Float.toString((float) Math.toDegrees(mOrientationAngles[0])));
        }
    }


    private void clearCompassHistory()
    {
        mCompassHistorySum[0] = 0;
        mCompassHistorySum[1] = 0;
        mCompassHistory.clear();
    }

    public float calculateAverageAngle()
    {
        int totalTerms = mCompassHistory.size();
        if (totalTerms > mHistoryMaxLength)
        {
            float firstTerm = mCompassHistory.removeFirst();
            mCompassHistorySum[0] -= Math.sin(firstTerm);
            mCompassHistorySum[1] -= Math.cos(firstTerm);
            totalTerms -= 1;
        }
        float lastTerm = mCompassHistory.getLast();
        mCompassHistorySum[0] += Math.sin(lastTerm);
        mCompassHistorySum[1] += Math.cos(lastTerm);
        float angle = (float) Math.atan2(mCompassHistorySum[0] / totalTerms, mCompassHistorySum[1] / totalTerms);

        return angle;
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    private class ShowLocationOnMapTask extends AsyncTask<Void,String,Void> {

        @Override
        protected Void doInBackground(Void... params) {
            String myLocationFromAsync;
            int i = 0;
            int check = 100000;
            while (true) {
                i++;
                if(i==check) {
                    i=0;
                    Location l = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    //Log.d("async", "doInB"+l.toString());
                    if(l != null)
                    {
                        myLocationFromAsync = new LatLng(l.getLatitude(),l.getLongitude()).toString();
                        publishProgress(myLocationFromAsync);
                    }
                }
                if(isCancelled()) {
                    break;
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... value) {
            String pom = value[0];
            pom = pom.replace("(","");
            pom = pom.replace(")","");
            pom = pom.replace("lat/lng: ","");
            String[] s = pom.split(",");
            LatLng latlng = new LatLng(Double.parseDouble(s[0]),Double.parseDouble(s[1]));
            //putMyLocationMarkerOnMap(latlng);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d(TAG,"onPostExecute: execute");
            Toast.makeText(AsyncTaskActivity.this, "AsyncTask Map Location Completed", Toast.LENGTH_SHORT).show();
        }
    }

}
