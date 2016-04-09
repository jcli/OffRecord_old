package com.sinova.jcli.offrecord;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {

    public static String TAG = MainActivity.class.getSimpleName();
    public GoogleDriveModel mGDriveModel=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        mGDriveModel = new GoogleDriveModel(this);

        //enable areas
        JCLog.enableLogArea(JCLog.LogAreas.UI);
        JCLog.enableLogArea(JCLog.LogAreas.GOOGLEAPI);

        JCLog.log(this, JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "onCreated called.");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        mGDriveModel.close();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        JCLog.log(this, JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "onActivityResult called requestCode:" + requestCode + " result code:" + resultCode);
        if (requestCode==GoogleDriveModel.REQUEST_CODE_RESOLUTION) {
            if (mGDriveModel != null) {
                mGDriveModel.close();
                mGDriveModel = null;
            }
            mGDriveModel = new GoogleDriveModel(this);
        }
    }

    @Override
    protected void onDestroy(){
        JCLog.log(this, JCLog.LogLevel.VERBOSE, JCLog.LogAreas.UI, "onDestroy() called");
        super.onDestroy();
    }
}
