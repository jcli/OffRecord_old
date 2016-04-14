package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.ExecutionOptions;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;

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

    // folder stack
    public class FolderInfo {
        public DriveFolder folder;
        public Metadata items[];
    }
    private ArrayDeque<FolderInfo> mFolderStack = new ArrayDeque<FolderInfo>();

    // current list children job id
    private int mCurrentListChildrenJobID;

    /////////// private state variable /////////////

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
        mCurrentListChildrenJobID=0;
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "connecting...");
    }

    /////////////////// public API ////////////////
    public void close(){
        mGoogleApiClient.disconnect();
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "GoogleApiClient disconnected.");
    }

    public boolean isConnected(){
        return mGoogleApiClient.isConnected();
    }

    public FolderInfo getCurrentFolder(){
        return mFolderStack.peek();
    }

    public void popFolderStack(){
        if (mFolderStack.size()>1){
            mFolderStack.pop();
            mCurrentFolder = mFolderStack.peek().folder;
            mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
        }else if (mFolderStack.size()==1){
            //should go to parent, unless we are at app root, then we listChildren only.
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "Going to find the parent of current folder.");
            mCurrentFolder.listParents(mGoogleApiClient).
                    setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                        @Override
                        public void onResult(DriveApi.MetadataBufferResult result) {
                            if (!result.getStatus().isSuccess()) {
                                return;
                            }
                            MetadataBuffer buffer = result.getMetadataBuffer();
                            if (buffer.getCount()>0){
                                // have parent. Check the first parent to see if it's drive root.
                                if(Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId() == buffer.get(0).getDriveId()){
                                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "Can't go higher because parent is drive root.");
                                }else{
                                    mFolderStack.pop();
                                    mCurrentFolder = buffer.get(0).getDriveId().asDriveFolder();
                                }
                            }else{
                                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Don't have parents when it should.");
                            }
                            mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
                            buffer.release();
                            result.release();
                        }
                    });
        }else{
            // empty folder stack.  Don't do anything
        }
    }

    public int getFolderStackSize(){
        return mFolderStack.size();
    }

    public void gotoFolderByTitle(String folderName){
        FolderInfo info = mFolderStack.peek();
        if (info == null){
            gotoAppRoot();
            return;
        }
        if (info.items!=null){
            for (Metadata data: info.items){
                if (data.getTitle().equals(folderName) && data.isFolder()){
                    // goto folder and list children
                    mCurrentFolder = data.getDriveId().asDriveFolder();
                    mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
                    break;
                }
            }
            // not found
        }else {
            // not found
        }

    }

    public void gotoAppRoot(){
        // // TODO: 4/14/16 need to solve re-entry problem
        mFolderStack.clear();
        // search for "OffRecord" folder
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, "OffRecord"))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                .build();
        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Searching for OffRecord app root...");
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
                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "app folder not found.");
                    mCurrentFolder = Drive.DriveApi.getRootFolder(mGoogleApiClient);
                    createFolder("OffRecord", true);
                }else if(count>1){
                    // error, multiple root folder.  What to do?
                }else{
                    // set current folder to app root, then list folder.
                    MetadataBuffer folderBuffer = result.getMetadataBuffer();
                    Metadata folderData = folderBuffer.get(0);
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "app folder found. name: " + folderData.getTitle());
                    mCurrentFolder=folderData.getDriveId().asDriveFolder();
                    mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
                }
                buffer.release();
            }
        });
    }

    public void createFolder(final String folderName, final boolean gotoFolder){
        if (mCurrentFolder!=null){
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(folderName).build();
            // check for conflict

            FolderInfo info = mFolderStack.peek();
            if (info != null && info.folder == mCurrentFolder && info.items!=null) {
                for (Metadata item: info.items){
                    if (item.getTitle()==folderName && item.isFolder()){
                        return;
                    }
                }
            }
            mCurrentFolder.createFolder(mGoogleApiClient, changeSet).setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                @Override
                public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                    if (!result.getStatus().isSuccess()) {
                        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Problem while trying to create a folder");
                        return;
                    }else{
                        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Created folder: "+ folderName);
                    }
                    if (gotoFolder) {
                        //set the current folder to app root
                        mCurrentFolder = result.getDriveFolder();
                    }
                    //list the current folder
                    mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());

                }
            });
        }else{
            gotoAppRoot();
        }
    }

    public void createTxtFile(final String fileName, String content){
        if (mCurrentFolder!=null){
            // check for conflict
            FolderInfo info = mFolderStack.peek();
            if (info!=null && info.folder==mCurrentFolder && info.items!=null){
                for (Metadata item: info.items){
                    if (item.getTitle() == fileName && !item.isFolder()){
                        return;
                    }
                }
            }
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(fileName)
                    .setMimeType("text/plain").build();
            mCurrentFolder.createFile(mGoogleApiClient, changeSet, null)
                    .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                        @Override
                        public void onResult(DriveFolder.DriveFileResult result) {
                            if (!result.getStatus().isSuccess()) {
                                // Handle error
                                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Problem while trying to create a file");
                                return;
                            }else{
                                JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Created file: "+ fileName);
                            }
                            //list the current folder
                            mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
                            //DriveFile file = result.getDriveFile();
                            // open the file and write
                        }
                    });
        }else {
            gotoAppRoot();
        }
    }

    public void deleteAppRoot() {
        //// TODO: 4/14/16 need to solve re-entry problem 
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, "OffRecord"))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                .build();
        Drive.DriveApi.getRootFolder(mGoogleApiClient)
                .queryChildren(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            return;
                        }
                        MetadataBuffer folderBuffer= result.getMetadataBuffer();
                        int count = folderBuffer.getCount();
                        if (count == 0) {
                            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "no app folder to delete.");
                            mCurrentFolder=null;
                            mFolderStack.clear();
                        } else {
                            // there should only be one app root
                            for (int i = 0; i < count; i++) {
                                Metadata folderData = folderBuffer.get(i);
                                folderData.getDriveId().asDriveFolder().
                                        delete(mGoogleApiClient).
                                        setResultCallback(new ResultCallback<Status>() {
                                            @Override
                                            public void onResult(@NonNull Status status) {
                                                if (status.isSuccess()){
                                                    mCurrentFolder=null;
                                                    mFolderStack.clear();
                                                    JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "app folder deleted.");
                                                }
                                            }
                                        });
                            }
                        }
                        folderBuffer.release();
                    }
                });
    }

    ////////////////// callbacks //////////////////
    @Override
    public void onConnected(Bundle bundle) {
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "Google Drive Connected.");
        // list current folder
        if (mCurrentFolder!=null){
            // list current directory
            mCurrentFolder.listChildren(mGoogleApiClient).setResultCallback(new ChildrenRetrievedCallback());
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "google drive connecting suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "google drive connection failed.");
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

    private class ChildrenRetrievedCallback implements ResultCallback<DriveApi.MetadataBufferResult> {

        private int mJobID;
        public ChildrenRetrievedCallback() {
            if (mCurrentListChildrenJobID < 1000) {
                mJobID = mCurrentListChildrenJobID + 1;
            }else{
                mJobID=0;
            }
            mCurrentListChildrenJobID=mJobID;
        }

        @Override
        public void onResult(DriveApi.MetadataBufferResult result) {
            // only modify the state if the result is for the current job.
            // otherwise discard the result
            if (mJobID == mCurrentListChildrenJobID) {
                if (!result.getStatus().isSuccess()) {
                    gotoAppRoot();
                    return;
                }
                // list the folder, and push it onto the stack.
                JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "List children result for job #"+String.valueOf(mJobID));
                MetadataBuffer buffer = result.getMetadataBuffer();
                FolderInfo info = mFolderStack.peek();
                if (info == null || info.folder != mCurrentFolder) {
                    info = new FolderInfo();
                    info.folder = mCurrentFolder;
                    mFolderStack.push(info);
                }
                int count = buffer.getCount();
                if (count > 0) {
                    //info.items = new FolderInfo.ItemInfo[count];
                    info.items = new Metadata[count];
                    for (int i = 0; i < count; i++) {
                        info.items[i] = buffer.get(i).freeze();
                    }
                }
                buffer.release();
                result.release();
            }else{
                result.release();
            }
        }
    }

}
