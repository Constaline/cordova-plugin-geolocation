/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */


package org.apache.cordova.geolocation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Geolocation extends CordovaPlugin {
  private static long LAST_LOCATION_CACHE_TIMEOUT = 5 * 60 * 1000; // 5 Minutes cache
  private static long LAST_CACHED_PRECISION_REQ_TIMEOUT = 5 * 1000; // 5 seconds request timeout for request high precision
  private static long START_TIMEOUT = 1000L;
  private static long INC_TIMEOUT = 1000L;
  private static long MAX_TIMEOUT = 1000L;
  private static String TAG = "GeolocationPlugin";
  private CallbackContext context;
  private String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
  private static int REQUEST_PERMISSIONS_ONLY = 0;
  private static int REQUEST_PERMISSIONS_AND_POSITION = 1;
  private LocationManager mLocationManager;
  private long mLastCachedPrecisionReqTimestamp = 0L;

  private LocationListener mListener2 = new LocationListener() {
    @Override
    public void onLocationChanged(@NonNull Location location) {
      Log.d(TAG, "IsMock = " + location.isFromMockProvider() + " - Accurancy = " + location.getAccuracy() + " Provider = " + location.getProvider());
      stopTimer();
      mLocationManager.removeUpdates(mListener2);
      PluginResult result = new PluginResult(PluginResult.Status.OK, returnLocationJSON(location));
      context.sendPluginResult(result);
    }
  };

  private long mCurrentTimeout = START_TIMEOUT;
  private Handler mRequestTimeoutHandler = new Handler();
  private Runnable mRequestTimeoutRunnable = new Runnable() {
    @Override
    public void run() {
      // Do AGPS Request...
      Log.d(TAG, "mRequestTimeoutRunnable.run after " + mCurrentTimeout + "ms");
      if (mCurrentTimeout < MAX_TIMEOUT) {
        mCurrentTimeout += INC_TIMEOUT;
      }
      mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, mListener2);
    }
  };



  LocationManager getLocationManager() {
    return mLocationManager;
  }



  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
  }



  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    LOG.d(TAG, "We are entering execute");
    context = callbackContext;
    if (action.equals("getPermission")) {
      if (hasPermisssion()) {
        PluginResult r = new PluginResult(PluginResult.Status.OK);
        context.sendPluginResult(r);
        return true;
      } else {
        PermissionHelper.requestPermissions(this, REQUEST_PERMISSIONS_ONLY, permissions);
      }
      return true;
    } else if (action.equals("getCurrentPosition")) {
      if (hasPermisssion()) {
//            PluginResult r = new PluginResult(PluginResult.Status.OK);
//            context.sendPluginResult(r);

        // TODO: Ask position
        getLastLocation(args, callbackContext);

        return true;
      } else {
        PermissionHelper.requestPermissions(this, REQUEST_PERMISSIONS_AND_POSITION, permissions);
      }
      return true;
    }
    return false;
  }



  public void onRequestPermissionResult(int requestCode, String[] permissions,
                                        int[] grantResults) throws JSONException {
    PluginResult result;
    //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
    if (context != null) {
      for (int r : grantResults) {
        if (r == PackageManager.PERMISSION_DENIED) {
          LOG.d(TAG, "Permission Denied!");
          result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
          context.sendPluginResult(result);
          return;
        }

      }
      if (requestCode == REQUEST_PERMISSIONS_AND_POSITION) {
        context.sendPluginResult(new PluginResult(PluginResult.Status.OK, returnLocationJSON()));
//              getLastLocation2(null, null);
      } else {
        result = new PluginResult(PluginResult.Status.OK);
        context.sendPluginResult(result);
      }
    }
  }



  public boolean hasPermisssion() {
    for (String p : permissions) {
      if (!PermissionHelper.hasPermission(this, p)) {
        return false;
      }
    }
    return true;
  }



  /*
   * We override this so that we can access the permissions variable, which no longer exists in
   * the parent class, since we can't initialize it reliably in the constructor!
   */

  public void requestPermissions(int requestCode) {
    PermissionHelper.requestPermissions(this, requestCode, permissions);
  }



  public JSONObject returnLocationJSON(Location loc) {
    JSONObject o = new JSONObject();
    JSONObject coords = new JSONObject();

    try {
      o.put("latitude", loc.getLatitude());
      o.put("longitude", loc.getLongitude());
      o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
      o.put("accuracy", loc.getAccuracy());
      o.put("heading",
        (loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
          : null) : null));
      o.put("velocity", loc.getSpeed());
      o.put("timestamp", loc.getTime());
      o.put("mocked", loc.isFromMockProvider());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Log.d(TAG, "isMocked = " + loc.isMock());
        o.put("mocked", loc.isMock());
      } else {
        Log.d(TAG, "isFromMockProvider = " + loc.isFromMockProvider());
        o.put("mocked", loc.isFromMockProvider());
      }
      coords.put("coords", o);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return coords;
  }



  public JSONObject returnLocationJSON() {
    JSONObject o = new JSONObject();
    JSONObject coords = new JSONObject();

    Log.d(TAG, "isMocked = UnKnown");

    try {
      o.put("latitude", 0);
      o.put("longitude", 0);
      o.put("accuracy", 1000);
      o.put("mocked", false);
      coords.put("coords", o);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return coords;
  }



  private void getLastLocation(JSONArray args, CallbackContext callbackContext) {
    try {
      boolean gpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
      Log.d(TAG, "gpsEnabled = " + gpsEnabled);
      if (!gpsEnabled) {
        PluginResult result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
        context.sendPluginResult(result);
        return;
      }

      long currentTime = System.currentTimeMillis();

      Log.d(TAG, "LAST_HIGH_PRECISION_TIMEOUT: " + currentTime + " " + (mLastCachedPrecisionReqTimestamp + LAST_CACHED_PRECISION_REQ_TIMEOUT));

      if (currentTime > mLastCachedPrecisionReqTimestamp + LAST_CACHED_PRECISION_REQ_TIMEOUT) {
        Log.d(TAG, "LAST_HIGH_PRECISION_TIMEOUT: can use cached position");
        mCurrentTimeout = START_TIMEOUT;
        Criteria criteria = new Criteria();
        String bestProvider = mLocationManager.getBestProvider(criteria, true);
        Location loc = mLocationManager.getLastKnownLocation(bestProvider);

        if (loc != null) {
          Log.d(TAG, "LastKnownLocation.accurancy= " + loc.getAccuracy());
          Log.d(TAG, "LastKnownLocation.accurancy= " + loc.getTime());

          long diff = currentTime - loc.getTime();
          Log.d(TAG, "LastKnownLocation.loc time= " + loc.getTime());
          Log.d(TAG, "LastKnownLocation.loc age= " + diff + " ms");
          Log.d(TAG, "LastKnownLocation.loc lat= " + loc.getLatitude());
          Log.d(TAG, "LastKnownLocation.loc lon= " + loc.getLongitude());

          if (diff < LAST_LOCATION_CACHE_TIMEOUT) {
            Log.d(TAG, "LastKnownLocation is ok, return cached position");
            PluginResult result = new PluginResult(PluginResult.Status.OK, returnLocationJSON(loc));
            context.sendPluginResult(result);
            mLastCachedPrecisionReqTimestamp = System.currentTimeMillis();
            return;
          } else {
            Log.d(TAG, "LastKnownLocation NULL ");
          }
        }
      } else {
        Log.d(TAG, "LAST_HIGH_PRECISION_TIMEOUT: already requested high precision... do not use cache for now...");
      }

      mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
        1000,          // 10-second interval.
        10,             // 10 meters.
        mListener2);
      startTimer();
      //mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, mListener2);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage(), e);
    }
  }



  private void startTimer() {
    Log.d(TAG, "stopTimer");
    mRequestTimeoutHandler.postDelayed(mRequestTimeoutRunnable, mCurrentTimeout);
  }



  private void stopTimer() {
    Log.d(TAG, "stopTimer");
    mRequestTimeoutHandler.removeCallbacks(mRequestTimeoutRunnable);
  }
}
