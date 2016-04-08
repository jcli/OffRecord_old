package com.sinova.jcli.offrecord;

import android.app.Application;
import android.content.Intent;
import android.support.v7.view.ContextThemeWrapper;
import android.test.ActivityUnitTestCase;
import android.test.ApplicationTestCase;
import android.test.UiThreadTest;
import android.util.Log;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */

public class GoogleDriveModelTest extends ActivityUnitTestCase<MainActivity> {

    public static String TAG = GoogleDriveModelTest.class.getSimpleName();
    private Intent mStartIntent;

    public GoogleDriveModelTest() {
        super(MainActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ContextThemeWrapper context = new ContextThemeWrapper(getInstrumentation().getTargetContext(), R.style.AppTheme_NoActionBar);
        setActivityContext(context);
        Log.v(TAG, "Setting up GoogleDriveModelTest");
    }

    @UiThreadTest
    public void testConnection() throws InterruptedException {
        Log.v(TAG, "testConnection...");

        mStartIntent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class);
        // Starts the MainActivity of the target application
        startActivity(mStartIntent, null, null);

        Thread.sleep(3000);
        assertEquals(true, true);
    }

    @Override
    public void tearDown() throws Exception {
        Log.v(TAG, "TearDown up GoogleDriveModelTest");
        super.tearDown();
    }
}