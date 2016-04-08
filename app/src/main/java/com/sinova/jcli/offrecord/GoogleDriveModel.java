package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFolder;


/**
 * Created by jcli on 4/7/16.
 */
public class GoogleDriveModel implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    public static String TAG = GoogleDriveModel.class.getSimpleName();
    public static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    public static final int REQUEST_CODE_CREATOR = 2;
    public static final int REQUEST_CODE_RESOLUTION = 3;
    private Activity mParentActivity = null;

    private DriveFolder mCurrentFolder=null;

    public GoogleDriveModel(Activity callerContext){
        mParentActivity = callerContext;
        mGoogleApiClient = new GoogleApiClient.Builder(mParentActivity)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();
        mGoogleApiClient.connect();
        Log.v(TAG, "connecting ...");
    }

    public void close(){
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.v(TAG, "google drive connected");
        // list current folder
        if (mCurrentFolder==null){
            // set it to root
            mCurrentFolder=Drive.DriveApi.getRootFolder(mGoogleApiClient);
        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.v(TAG, "google drive connecting suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.v(TAG, "google drive connecting failed");
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(mParentActivity, REQUEST_CODE_RESOLUTION);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), mParentActivity, 0).show();
        }
    }
}
