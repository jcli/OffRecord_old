package com.sinova.jcli.offrecord;

import android.app.Application;
import android.content.Intent;
import android.support.v7.view.ContextThemeWrapper;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ActivityUnitTestCase;
import android.test.AndroidTestCase;
import android.test.ApplicationTestCase;
import android.test.UiThreadTest;
import android.util.Log;

import junit.framework.Assert;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */

public class GoogleDriveModelTest extends ActivityInstrumentationTestCase2<MainActivity> {

    public static String TAG = GoogleDriveModelTest.class.getSimpleName();
    private Intent mStartIntent;

    public GoogleDriveModelTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Log.w(TAG,"Setting up GoogleDriveModelTest");
    }

    public void testActivityNotNull(){
        MainActivity activity = getActivity();
        assertNotNull(activity);
    }

    public void testAll() throws InterruptedException {
        mTestGoogleDriveConnected();
//        mTestCurrentFolderIsRoot();
    }

    @Override
    public void tearDown() throws Exception {
        Log.w(TAG, "TearDown GoogleDriveModelTest");
        super.tearDown();
    }

////////////// actual tests ////////////////

    private void mTestGoogleDriveConnected() throws InterruptedException {
        MainActivity activity = getActivity();
        for (int i=0; i<100; i++){
            if(activity.mGDriveModel.isConnected()) break;
            Thread.sleep(1000);
        }
        assertTrue(activity.mGDriveModel.isConnected());
    }

    private void mTestCurrentFolderIsRoot(){
        MainActivity activity = getActivity();
        assertEquals(activity.mGDriveModel.getRootFolder(), activity.mGDriveModel.getCurrentFolder());
    }


}