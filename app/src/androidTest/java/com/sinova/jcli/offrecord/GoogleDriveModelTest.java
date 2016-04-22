package com.sinova.jcli.offrecord;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.google.android.gms.drive.Metadata;

import java.util.ArrayList;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */

public class GoogleDriveModelTest extends ActivityInstrumentationTestCase2<MainActivity> {

    public static String TAG = GoogleDriveModelTest.class.getSimpleName();
    private Intent mStartIntent;

    public GoogleDriveModelTest() {
        super(MainActivity.class);
    }

    MainActivity mActivity;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        Log.w(TAG,"Setting up GoogleDriveModelTest");
    }

    public void testAll() throws InterruptedException {
        // test to see if we are connected.
        mTestGoogleDriveConnected();
        // delete the app root
        mTestDeleteAppRoot();
        // goto the app root
        mTestGotoAppRoot();
        // create some folders
        mTestCreateFolders("level_1_folder_");
        // create some files
        mTestCreateFiles("level_1_file_");
        // goto one level down
        mActivity.mGDriveModel.gotoFolderByTitle("level_1_folder_1");
        // create some folders
        mTestCreateFolders("level_2_folder_");
        // create some files
        mTestCreateFiles("level_2_file_");
        // goto one level down
        mActivity.mGDriveModel.gotoFolderByTitle("level_2_folder_0");
        // create some folders
        mTestCreateFolders("level_3_folder_");
        // create some files
        mTestCreateFiles("level_3_file_");
        // check folder stack size
        Log.w(TAG, "Current folder stack depth: " + String.valueOf(mActivity.mGDriveModel.getFolderStackSize()));
        assertEquals(3, mActivity.mGDriveModel.getFolderStackSize());

        mPrintCurrentFolder();

        mActivity.mGDriveModel.popFolderStack();
        Log.w(TAG, "Current folder stack depth: " + String.valueOf(mActivity.mGDriveModel.getFolderStackSize()));
        assertEquals(2, mActivity.mGDriveModel.getFolderStackSize());
        mPrintCurrentFolder();

        mActivity.mGDriveModel.popFolderStack();
        Log.w(TAG, "Current folder stack depth: " + String.valueOf(mActivity.mGDriveModel.getFolderStackSize()));
        assertEquals(1, mActivity.mGDriveModel.getFolderStackSize());
        mPrintCurrentFolder();

        mActivity.mGDriveModel.popFolderStack();
        Log.w(TAG, "Current folder stack depth: " + String.valueOf(mActivity.mGDriveModel.getFolderStackSize()));
        assertEquals(1, mActivity.mGDriveModel.getFolderStackSize());
        mPrintCurrentFolder();

        Thread.sleep(2000);
        Log.w(TAG, "Current folder stack depth after 2 second delay: " + String.valueOf(mActivity.mGDriveModel.getFolderStackSize()));
        assertEquals(1, mActivity.mGDriveModel.getFolderStackSize());
        mPrintCurrentFolder();

    }

    @Override
    public void tearDown() throws Exception {
        Log.w(TAG, "TearDown GoogleDriveModelTest");
        super.tearDown();
        Thread.sleep(1000);
    }

////////////// actual tests ////////////////

    private void mTestGoogleDriveConnected() throws InterruptedException {
        for (int i=0; i<100; i++){
            if(mActivity.mGDriveModel.isConnected()) break;
            Thread.sleep(1000);
        }
        Thread.sleep(2000);
        assertTrue(mActivity.mGDriveModel.isConnected());
    }

    private void mTestDeleteAppRoot() throws InterruptedException {
        mActivity.mGDriveModel.deleteAppRoot();
        Thread.sleep(2000);
        assertNull(mActivity.mGDriveModel.getCurrentFolder());
    }

    private void mTestGotoAppRoot() throws InterruptedException {
        Log.w(TAG, "Testing goto app root...");
        mActivity.mGDriveModel.gotoAppRoot();
        Thread.sleep(2000);
        assertNotNull(mActivity.mGDriveModel.getCurrentFolder());
    }

    private void mTestCreateFolders(String prefix) throws InterruptedException {
        Log.w(TAG, "Testing creating folders in app root...");
        ArrayList<String> expectedTitles = new ArrayList<String>();
        for (int i=0; i<3; i++){
            String title = prefix+ String.valueOf(i);
            Log.w(TAG, "creating folder: " + title);
            expectedTitles.add(title);
            mActivity.mGDriveModel.createFolderInCurrentFolder(title, false);
        }
        Thread.sleep(2000);
        GoogleDriveModel.FolderInfo info = mActivity.mGDriveModel.getCurrentFolder();
        assertNotNull(info);
        assertEquals(3, info.items.length);
        ArrayList<String> actualTitles= new ArrayList<String>();
        for (Metadata data: info.items){
            actualTitles.add(data.getTitle());
        }

        for (String expectedTitle: expectedTitles){
            assertTrue(actualTitles.contains(expectedTitle));
        }

        Thread.sleep(2000);
    }

    private void mTestCreateFiles(String prefix) throws InterruptedException {
        Log.w(TAG, "Testing creating folders in app root...");
        ArrayList<String> expectedTitles = new ArrayList<String>();
        for (int i=0; i<3; i++){
            String title = prefix+ String.valueOf(i);
            Log.w(TAG, "creating file: " + title);
            expectedTitles.add(title);
            mActivity.mGDriveModel.createTxtFile(title, "");
        }
        Thread.sleep(2000);
        GoogleDriveModel.FolderInfo info = mActivity.mGDriveModel.getCurrentFolder();
        Log.w(TAG, "current folder have this many items: "+String.valueOf(info.items.length));
        assertNotNull(info);
        assertEquals(6, info.items.length);
        ArrayList<String> actualTitles= new ArrayList<String>();
        for (Metadata data: info.items){
            actualTitles.add(data.getTitle());
        }

        for (String expectedTitle: expectedTitles){
            assertTrue(actualTitles.contains(expectedTitle));
        }

        Thread.sleep(2000);
    }

    private void mPrintCurrentFolder(){
        GoogleDriveModel.FolderInfo info = mActivity.mGDriveModel.getCurrentFolder();
        if (info.items==null){
            Log.w(TAG, "folder empty...");
        }else{
            for (Metadata data: info.items){
                Log.w(TAG, "Title: "+data.getTitle()+", isFolder: "+String.valueOf(data.isFolder()));
            }
        }
    }

}