package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Observable;

/**
 * Created by jcli on 4/7/16.
 */
public class GoogleDriveModel extends Observable implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    public static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    public static final int REQUEST_CODE_CREATOR = 2;
    public static final int REQUEST_CODE_RESOLUTION = 3;
    private Activity mParentActivity = null;

    // folder stack
    public class FolderInfo {
        public DriveFolder parentFolder;
        public DriveFolder folder;
        public Metadata items[];
    }

    /////////////// constructor ////////////////////
    public GoogleDriveModel(Activity callerContext){
        mParentActivity = callerContext;
        mGoogleApiClient = new GoogleApiClient.Builder(mParentActivity)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();
    }

    /////////////////// public API that I will keep////////////////
    public void open(){
        mGoogleApiClient.connect();
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "connecting...");
    }

    public void close(){
        mGoogleApiClient.disconnect();
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "GoogleApiClient disconnected.");
    }

    public boolean isConnected(){
        return mGoogleApiClient.isConnected();
    }

    public interface SearchFolderForAssetByNameCallback {
        void callback(String[] assetIDs);
    }
    public void searchFolderForFolderByName(String folderIDStr, String assetName, final SearchFolderForAssetByNameCallback callbackInstance){
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, assetName))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"))
                .build();
        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Searching for: " + assetName);
        DriveId.decodeFromString(folderIDStr)
                .asDriveFolder().queryChildren(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            @Override
            public void onResult(DriveApi.MetadataBufferResult result) {
                if (!result.getStatus().isSuccess()) {
                    if (callbackInstance!=null) callbackInstance.callback(new String[0]);
                    return;
                }
                MetadataBuffer buffer = result.getMetadataBuffer();
                int count=buffer.getCount();
                String assetIDs[] = new String[count];
                for (int i=0; i<count; i++){
                    assetIDs[i]=new String(buffer.get(0).getDriveId().encodeToString());
                }
                if (callbackInstance!=null) callbackInstance.callback(assetIDs);
                buffer.release();
            }
        });
    }

    public interface ListParentByIDCallback{
        void callback (DriveFolder parent);
    }
    public void listParentByID (String assetIDStr, final ListParentByIDCallback callbackInstance){
        DriveId.decodeFromString(assetIDStr).asDriveResource().listParents(mGoogleApiClient).
                setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            if (callbackInstance!=null) callbackInstance.callback(null);
                            return;
                        }
                        MetadataBuffer buffer = result.getMetadataBuffer();
                        if (buffer.getCount()>0){
                            // have parents. return the first parent.
                            if (callbackInstance!=null) callbackInstance.callback(buffer.get(0).getDriveId().asDriveFolder());
                        }else{
                            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Don't have parents.");
                            if (callbackInstance!=null) callbackInstance.callback(null);
                        }
                        buffer.release();
                        result.release();
                    }
                });
    }

    public interface ListFolderByIDCallback {
        void callback(FolderInfo info);
    }
    public void listFolderByID (String folderIDStr, final ListFolderByIDCallback callbackInstance) {
        final FolderInfo currentFolder = new FolderInfo();
        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "listing folder: "+folderIDStr);
        currentFolder.folder = DriveId.decodeFromString(folderIDStr).asDriveFolder();
        // list parents first
        listParentByID(folderIDStr, new ListParentByIDCallback() {
            @Override
            public void callback(DriveFolder parent) {
                currentFolder.parentFolder = parent;
                // then list children
                currentFolder.folder.listChildren(mGoogleApiClient)
                        .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                            @Override
                            public void onResult(DriveApi.MetadataBufferResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    currentFolder.items = new Metadata[0];
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                    return;
                                }
                                MetadataBuffer buffer = result.getMetadataBuffer();
                                if (buffer.getCount()>0){
                                    currentFolder.items = new Metadata[buffer.getCount()];
                                    for (int i=0; i<buffer.getCount(); i++){
                                        currentFolder.items[i]=buffer.get(i).freeze();
                                    }
                                    // sort items
                                    Arrays.sort(currentFolder.items, new Comparator<Metadata>() {
                                        @Override
                                        public int compare(Metadata lhs, Metadata rhs) {
                                            if (lhs.isFolder() == rhs.isFolder()) {
                                                return lhs.getTitle().compareTo(rhs.getTitle());
                                            } else {
                                                if (lhs.isFolder()) {
                                                    return -1;
                                                } else {
                                                    return 1;
                                                }
                                            }
                                        }
                                    });
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                }else{
                                    currentFolder.items = new Metadata[0];
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                }
                                buffer.release();
                                result.release();
                            }
                        });
            }
        });
    }

    public void createFolderInFolder(final String name, final String folderIdStr, final boolean gotoFolder, final ListFolderByIDCallback callbackInstance){
        // check for naming conflict
        listFolderByID(folderIdStr, new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                for (int i=0; i<info.items.length; i++){
                    if (info.items[i].getTitle().equals(name) && info.items[i].isFolder()){
                        // naming conflict !!
                        if (callbackInstance!=null) callbackInstance.callback(null);
                        return;
                    }
                }
                // no conflict if it gets to here
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(name).build();
                DriveId.decodeFromString(folderIdStr).asDriveFolder()
                        .createFolder(mGoogleApiClient, changeSet)
                        .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                    @Override
                    public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                        if (!result.getStatus().isSuccess()) {
                            JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Problem while trying to create folder: " + name);
                            if (callbackInstance!=null) callbackInstance.callback(null);
                            return;
                        }else{
                            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Created folder: "+ name);
                            if (gotoFolder){
                                // list newly created folder
                                listFolderByID(result.getDriveFolder().getDriveId().encodeToString(), callbackInstance);
                            }else{
                                // list current folder again
                                listFolderByID(folderIdStr, callbackInstance);
                            }
                        }
                    }
                });
            }
        });



    }

    public void searchCreateFolders(final String names[], final String folderIDStr, final ListFolderByIDCallback callbackInstance) {
        if (names.length > 0) {
            final String name = new String(names[0]);
            searchFolderForFolderByName(folderIDStr, name, new SearchFolderForAssetByNameCallback() {
                @Override
                public void callback(String[] assetIDs) {
                    if (assetIDs.length==0){
                        // not found.  Create it.
                        createFolderInFolder(name, folderIDStr, true, new ListFolderByIDCallback() {
                            @Override
                            public void callback(FolderInfo info) {
                                String newNames[] = Arrays.copyOfRange(names, 1, names.length);
                                // recursive
                                searchCreateFolders(newNames, info.folder.getDriveId().encodeToString(), callbackInstance);
                            }
                        });
                    }else{
                        // found. goto the next level
                        String newNames[] = Arrays.copyOfRange(names, 1, names.length);
                        searchCreateFolders(newNames, assetIDs[0], callbackInstance);
                    }
                }
            });
        }else{
            listFolderByID(folderIDStr, callbackInstance);
        }
    }

    public void listSectionRoot(final String sectionName, ListFolderByIDCallback callbackInstance){
        // find app root in drive root.  Create it if not found.
        final String driveRoot = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId().encodeToString();
        String[] names = {mParentActivity.getString(R.string.app_name), sectionName};
        searchCreateFolders(names, driveRoot, callbackInstance);
    }

    public interface ReadTxtFileCallback {
        void callback(String fileContent);
    }
    public void readTxtFile(final String assetID, final ReadTxtFileCallback callbackInstance){
        final DriveFile file = DriveId.decodeFromString(assetID).asDriveFile();
        file.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null)
                .setResultCallback(new ResultCallback<DriveContentsResult>() {
                    @Override
                    public void onResult(DriveContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            // display an error saying file can't be opened
                            return;
                        }
                        // DriveContents object contains pointers
                        // to the actual byte stream
                        DriveContents contents = result.getDriveContents();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()));
                        StringBuilder builder = new StringBuilder();
                        String line;
                        try {
                            while ((line = reader.readLine()) != null) {
                                builder.append(line);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        String contentsAsString = builder.toString();
                        contents.discard(mGoogleApiClient);
                        if (callbackInstance!=null) {
                            callbackInstance.callback(contentsAsString);
                        }
                    }
                });
    }

    public interface WriteTxtFileCallback {
        void callback(boolean success);
    }
    public void writeTxtFile(final String assetID, final String contentStr, final WriteTxtFileCallback callbackInstance){
        DriveFile file = DriveId.decodeFromString(assetID).asDriveFile();
        file.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(new ResultCallback<DriveContentsResult>() {
            @Override
            public void onResult(DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    // Handle error
                    return;
                }
                DriveContents driveContents = result.getDriveContents();
                try{
                    ParcelFileDescriptor parcelFileDescriptor = driveContents.getParcelFileDescriptor();
                    FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor
                            .getFileDescriptor());
                    Writer writer = new OutputStreamWriter(fileOutputStream);
                    writer.write(contentStr);
                    writer.flush();
                    writer.close();
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setLastViewedByMeDate(new Date()).build();
                driveContents.commit(mGoogleApiClient, changeSet).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status result) {
                        if (callbackInstance!=null){
                            callbackInstance.callback(result.isSuccess());
                        }
                    }
                });
            }
        });
    }

    /////////////////////////////////////////////////////////////////////////////

    ////////////////// callbacks //////////////////
    @Override
    public void onConnected(Bundle bundle) {
        //TODO: need to better notify connection
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "Google Drive Connected.");
        setChanged();
        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "observers notified.");
        notifyObservers();
        clearChanged();
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

}
