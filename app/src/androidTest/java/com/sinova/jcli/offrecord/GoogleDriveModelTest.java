package com.sinova.jcli.offrecord;

import android.app.Activity;
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

    MainActivity mActivity;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        Log.w(TAG,"Setting up GoogleDriveModelTest");
    }

    public void testAll() throws InterruptedException {
        mTestGoogleDriveConnected();
        //mTestDeleteAppRoot();
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
        Thread.sleep(4000);
        assertTrue(mActivity.mGDriveModel.isConnected());
    }

    private void mTestDeleteAppRoot() throws InterruptedException {
        mActivity.mGDriveModel.deleteAppRoot();
        Thread.sleep(2000);
        assertTrue(true);
    }
}