package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

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

    // current folder
    private DriveFolder mCurrentFolder=null;
    // folder list stack


    /////////// private state variable /////////////
    private boolean mSearchingAppRoot=false;

    /////////////// constructor ////////////////////
    public GoogleDriveModel(Activity callerContext){
        mParentActivity = callerContext;
        mGoogleApiClient = new GoogleApiClient.Builder(mParentActivity)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();
        mGoogleApiClient.connect();
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "connecting...");
    }

    /////////////////// public API ////////////////
    public void close(){
        mGoogleApiClient.disconnect();
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "GoogleApiClient disconnected.");
    }

    public boolean isConnected(){
        return mGoogleApiClient.isConnected();
    }

    public String getCurrentFolder(){
        return mCurrentFolder.toString();
    }

    public String getRootFolder(){
        return Drive.DriveApi.getRootFolder(mGoogleApiClient).toString();
    }

    ////////////////// callbacks //////////////////
    @Override
    public void onConnected(Bundle bundle) {
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "Google Drive Connected.");
        // list current folder
        if (mCurrentFolder==null){
            // TODO: clear folder stack
            // search for "OffRecord" folder
            Query query = new Query.Builder()
                    .addFilter(Filters.eq(SearchableField.TITLE, "OffRecord"))
                    .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                    .build();
            mSearchingAppRoot = true;
            JCLog.log(this, JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Searching for OffRecord app root...");
            Drive.DriveApi.getRootFolder(mGoogleApiClient).queryChildren(mGoogleApiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        return;
                    }
                    MetadataBuffer buffer = result.getMetadataBuffer();
                    int count=buffer.getCount();
                    if (count==0){
                        // not found, need to create folder
                        JCLog.log(this, JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "app folder not found.");
                    }else if(count>1){
                        // error, multiple root folder.  What to do?
                    }else{
                        // set current folder to app root, then list folder.
                    }
                }
            });
        }else {
            // list current directory
            //mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "google drive connecting suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "google drive connection failed.");
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
