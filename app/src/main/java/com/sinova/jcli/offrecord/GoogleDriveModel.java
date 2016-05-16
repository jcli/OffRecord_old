package com.sinova.jcli.offrecord;

import android.app.Activity;
import android.content.ClipData;
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
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.Observable;

/**
 * Created by jcli on 4/7/16.
 */
public class GoogleDriveModel extends Observable implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    protected GoogleApiClient mGoogleApiClient;
    public static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    public static final int REQUEST_CODE_CREATOR = 2;
    public static final int REQUEST_CODE_RESOLUTION = 3;
    protected Activity mParentActivity = null;
    protected DriveFolder mAppRootFolder;

    public class ItemInfo {
        public Metadata meta;
        public String readableTitle;
    }
    public class FolderInfo {
        public DriveFolder parentFolder;
        public DriveFolder folder;
        public ItemInfo items[];
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
                                    currentFolder.items = new ItemInfo[0];
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                    return;
                                }
                                MetadataBuffer buffer = result.getMetadataBuffer();
                                if (buffer.getCount()>0){
                                    currentFolder.items = new ItemInfo[buffer.getCount()];
                                    for (int i=0; i<buffer.getCount(); i++){
                                        currentFolder.items[i] = new ItemInfo();
                                        currentFolder.items[i].meta=buffer.get(i).freeze();
                                        currentFolder.items[i].readableTitle = currentFolder.items[i].meta.getTitle();
                                    }
                                    // sort items
                                    Arrays.sort(currentFolder.items, new Comparator<ItemInfo>() {
                                        @Override
                                        public int compare(ItemInfo lhs, ItemInfo rhs) {
                                            if (lhs.meta.isFolder() == rhs.meta.isFolder()) {
                                                return lhs.readableTitle.compareTo(rhs.readableTitle);
                                            } else {
                                                if (lhs.meta.isFolder()) {
                                                    return -1;
                                                } else {
                                                    return 1;
                                                }
                                            }
                                        }
                                    });
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                }else{
                                    currentFolder.items = new ItemInfo[0];
                                    if (callbackInstance!=null) callbackInstance.callback(currentFolder);
                                }
                                buffer.release();
                                result.release();
                            }
                        });
            }
        });
    }

    public void createFolderInFolder(final String name, final String folderIdStr, final boolean gotoFolder,
                                     final Map<String, String> metaInfo, final ListFolderByIDCallback callbackInstance){
        // TODO: can not re-entry
        // check for naming conflict
        listFolderByID(folderIdStr, new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                for (int i=0; i<info.items.length; i++){
                    if (nameCompare(name, info.items[i].meta, metaInfo) && info.items[i].meta.isFolder()){
                        // naming conflict !!
                        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "folder creation name conflict!");
                        if (gotoFolder){
                            // list conflicted folder
                            listFolderByID(info.items[i].meta.getDriveId().encodeToString(), callbackInstance);
                        }else{
                            // list current folder again
                            listFolderByID(folderIdStr, callbackInstance);
                        }
                        return;
                    }
                }
                // no conflict if it gets to here
                MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder();
                builder.setTitle(name);
                if (metaInfo!=null){
                    for (Map.Entry<String, String> entry : metaInfo.entrySet()){
                        CustomPropertyKey propertyKey = new CustomPropertyKey(entry.getKey(), CustomPropertyKey.PUBLIC);
                        builder.setCustomProperty(propertyKey, entry.getValue());
                    }
                }
                MetadataChangeSet changeSet = builder.build();

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
    public void createFolderInFolder(final String name, final String folderIdStr, final boolean gotoFolder,
                                     final ListFolderByIDCallback callbackInstance){
        createFolderInFolder(name, folderIdStr, gotoFolder, null, callbackInstance);
    }


    public void createTxtFileInFolder(final String fileName, final String folderIdStr,
                                      final Map<String, String> metaInfo, final ListFolderByIDCallback callbackInstance){
        // TODO: can not re-entry
        // check for naming conflict
        listFolderByID(folderIdStr, new ListFolderByIDCallback() {
            @Override
                public void callback(FolderInfo info) {
                for (int i = 0; i < info.items.length; i++) {
                    if (nameCompare(fileName, info.items[i].meta, metaInfo) && !info.items[i].meta.isFolder()) {
                        // naming conflict !!
                        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "Naming conflic for creating file!");
                        if (callbackInstance != null) callbackInstance.callback(null);
                        return;
                    }
                }
                // no conflict if it gets to here
                MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder();
                builder.setTitle(fileName);
                builder.setMimeType("text/plain");
                if (metaInfo!=null){
                    for (Map.Entry<String, String> entry : metaInfo.entrySet()){
                        CustomPropertyKey propertyKey = new CustomPropertyKey(entry.getKey(), CustomPropertyKey.PUBLIC);
                        builder.setCustomProperty(propertyKey, entry.getValue());
                    }
                }
                MetadataChangeSet changeSet = builder.build();

                DriveId.decodeFromString(folderIdStr).asDriveFolder()
                        .createFile(mGoogleApiClient, changeSet, null)
                        .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                            @Override
                            public void onResult(DriveFolder.DriveFileResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Problem while trying to create file: " + fileName);
                                    if (callbackInstance!=null) callbackInstance.callback(null);
                                    return;
                                }else{
                                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "Created file: "+ fileName);
                                    // list current folder again
                                    listFolderByID(folderIdStr, callbackInstance);
                                }
                            }
                        });
            }
        });
    }
    public void createTxtFileInFolder(final String fileName, final String folderIdStr, final ListFolderByIDCallback callbackInstance){
        createTxtFileInFolder(fileName, folderIdStr, null, callbackInstance);
    }

    protected void initAppRoot(ListFolderByIDCallback callbackInstance){
        final String driveRoot = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId().encodeToString();
        String name = mParentActivity.getString(R.string.app_name);
        createFolderInFolder(name, driveRoot, true, callbackInstance);
    }
    protected void initSectionRoot(String sectionRoot, ListFolderByIDCallback callbackInstance){
        // mAppRootFolder can not be null.
        if (mAppRootFolder !=null) {
            createFolderInFolder(sectionRoot, mAppRootFolder.getDriveId().encodeToString(), true, callbackInstance);
        }
    }

    public interface ReadTxtFileCallback {
        void callback(String fileContent);
    }
    public void readTxtFile(final ItemInfo assetInfo, final ReadTxtFileCallback callbackInstance){
        String assetID = assetInfo.meta.getDriveId().encodeToString();
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
    public void writeTxtFile(final ItemInfo assetInfo, final String contentStr, final WriteTxtFileCallback callbackInstance){
        writeTxtFile(assetInfo, contentStr, callbackInstance, null);
    }
    public void writeTxtFile(final ItemInfo assetInfo, final String contentStr, final WriteTxtFileCallback callbackInstance, final Map<String, String> metaInfo){
        String assetID = assetInfo.meta.getDriveId().encodeToString();
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
                MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder()
                        .setLastViewedByMeDate(new Date());
                if (metaInfo!=null){
                    for (Map.Entry<String, String> entry : metaInfo.entrySet()){
                        CustomPropertyKey propertyKey = new CustomPropertyKey(entry.getKey(), CustomPropertyKey.PUBLIC);
                        builder.setCustomProperty(propertyKey, entry.getValue());
                    }
                }
                MetadataChangeSet changeSet = builder.build();

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

    public void deleteItem(String assetID, ResultCallback<Status> callbackInstance){
        DriveResource driveResource= DriveId.decodeFromString(assetID).asDriveResource();
        driveResource.delete(mGoogleApiClient).setResultCallback(callbackInstance);
    }
    public void deleteMultipleItems(final Deque<String> items, final ResultCallback<Status> callbackInstance){
        String assetID = items.pop();
        deleteItem(assetID, new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (!status.isSuccess()){
                    JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "delete item failed!! " + status.getStatusMessage());
                    callbackInstance.onResult(status);
                }else {
                    if (items.size() == 0) {
                        //end case
                        callbackInstance.onResult(status);
                    } else {
                        deleteMultipleItems(items, callbackInstance);
                    }
                }
            }
        });
    }
    public void deleteEverything(){
        final String driveRoot = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId().encodeToString();
        listFolderByID(driveRoot, new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                if (info.items!=null){
                    Deque<String> itemStrings = new ArrayDeque<String>();
                    for (ItemInfo item: info.items){
                        itemStrings.push(item.meta.getDriveId().encodeToString());
                    }
                    deleteMultipleItems(itemStrings, new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            if (status.isSuccess()){
                                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "Everything deleted!!!");
                            }else{
                                JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "delete item failed!! " + status.getStatusMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////////////////

    // override this if you want put encryption data in folder title
    //protected boolean nameCompare(String name, String folderTitle){
    protected boolean nameCompare(String name, Metadata item, Map<String, String> metaInfo){
        return item.getTitle().equals(name);
    }

    ////////////////// callbacks //////////////////
    @Override
    public void onConnected(Bundle bundle) {
        //TODO: need to better notify connection
        initAppRoot(new ListFolderByIDCallback() {
            @Override
            public void callback(FolderInfo info) {
                if (info!=null){
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "app root initialized");
                    mAppRootFolder = info.folder;
                }
                JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.GOOGLEAPI, "Google Drive Connected.");
                setChanged();
                JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.GOOGLEAPI, "observers notified.");
                notifyObservers();
                clearChanged();
            }
        });
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
