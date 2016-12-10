package com.example.pawe.asynctaskapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
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

import java.net.URL;
import java.util.ArrayList;

public class AsyncTaskActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener,SensorEventListener{

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Location mCurrentLocation;
    float illuminance;
    boolean locationSettingsOk;
    protected static final int LOCATION_REQUEST_CODE = 1;
    protected static final int SETTINGS_CHECK_REQUEST_CODE = 2;
    private Marker marker;
    ShowLocationOnMapTask mapLocAsyncTask;
    private ArrayList<LatLng> traveledRoad;
    public static final String EXTRA_MESSAGE_ROAD_TRAVELED = "com.example.pawe.asyntaskapp.ROAD_TRAVELED";
    private SensorManager mSensorManager;
    private Sensor mLight;
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
                        locationSettingsOk = true;
                        Log.d("onResult","result ok");
                        break;
                    }
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            Log.d("onResult","result resolution required");
                            status.startResolutionForResult(
                                    AsyncTaskActivity.this,SETTINGS_CHECK_REQUEST_CODE
                            );

                        } catch (IntentSender.SendIntentException e){

                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE: {
                        Log.d("onResult","result change unavailable");
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        try{
            stopLocationUpdates();
            if(mapLocAsyncTask != null)
            {
                mapLocAsyncTask.execute();
            }
        }catch (IllegalStateException e){

        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);

        try{
            startLocationsUpdates();
            if(mapLocAsyncTask != null)
            {
                mapLocAsyncTask.execute();
            }
        }catch (IllegalStateException e){

        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED){
            locationSettingsOk = true;
            Log.d("mapReady","permission ok");
        }
        else{
            locationSettingsOk = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST_CODE);
            Log.d("mapReady","requestPermissions");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SETTINGS_CHECK_REQUEST_CODE: {
                if(resultCode == RESULT_OK) {
                    locationSettingsOk = true;
                    Log.d("activityResult","ok");
                }
                else {
                    locationSettingsOk = false;
                    Log.d("activityResult","not ok");
                    Toast.makeText(this, "ActivityResult not ok", Toast.LENGTH_SHORT).show();
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
                    locationSettingsOk = true;
                    Log.d("requestPermissionResult","ok");
                }
                else{
                    locationSettingsOk = false;
                    Toast.makeText(this, "Permission denied. Location is unavailable",
                            Toast.LENGTH_SHORT).show();
                    Log.d("requestPermissionResult","not ok");
                }
            }
        }
    }

    protected void startLocationsUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest,this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
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
        if (locationSettingsOk) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(mCurrentLocation != null) {
                LatLng ll = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                MarkerOptions markerO = new MarkerOptions().position(ll);
                marker = mMap.addMarker(markerO);
                startLocationsUpdates();
                traveledRoad = new ArrayList<>();
                traveledRoad.add(ll);
                if(mapLocAsyncTask == null) {
                    Log.d("newAsync","nnn");
                    mapLocAsyncTask = new ShowLocationOnMapTask();
                    mapLocAsyncTask.execute();
                }
            }
        }


    }

    public void showMapActivity(View view) {
        Intent intent = new Intent(AsyncTaskActivity.this,MapsActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_MESSAGE_ROAD_TRAVELED,traveledRoad);
        startActivity(intent);
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
            String pom = value[0].toString();
            pom = pom.replace("(","");
            pom = pom.replace(")","");
            pom = pom.replace("lat/lng: ","");
            //Log.d("aaaa",pom);
            String[] s = pom.split(",");
            LatLng latlng = new LatLng(Double.parseDouble(s[0]),Double.parseDouble(s[1]));
           //Log.d("aaaa",latlng.toString());
            marker.setPosition(latlng);
            //Log.d("progress", "update");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d("post", "execute");
            Toast.makeText(AsyncTaskActivity.this, "AsyncTask Map Location Completed", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onLocationChanged(Location location) {
        try {
            mCurrentLocation = location;
            LatLng latlng;
            traveledRoad.add(latlng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
            CameraPosition cameraposition = new CameraPosition.Builder()
                    .target(latlng)
                    .zoom(15)
                    .build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraposition));
        }catch (NullPointerException e){

    }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        illuminance = event.values[0];
        Log.d("illum",Float.toString(illuminance));
        setIlluminanceBar(illuminance);
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
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


}
