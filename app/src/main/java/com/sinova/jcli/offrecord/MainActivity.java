package com.sinova.jcli.offrecord;

import android.content.Intent;
import android.os.Bundle;
//import android.support.design.widget.FloatingActionButton;
import android.os.Debug;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static String TAG = MainActivity.class.getSimpleName();
    public static GoogleDriveModel mGDriveModel;

    private ViewPager mViewPager;
    private OffRecordPagerAdapter mPagerAdapter;
    private TabLayout mTabLayout;

    private MainActivityFragmentNotes mNotesFragment;
    private MainActivityFragmentChats mChatsFragment;
    private MainActivityFragmentPeople mPeopleFragment;

    private class OffRecordPagerAdapter extends FragmentPagerAdapter{

        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public OffRecordPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //enable areas
        JCLog.enableLogArea(JCLog.LogAreas.UI);
        JCLog.enableLogArea(JCLog.LogAreas.GOOGLEAPI);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (mGDriveModel==null) {
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "Fresh app start.  Creating GoogleDriveModel.");
            mGDriveModel = new GoogleDriveModel(this);
        }

        // setup tabs
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mPagerAdapter = new OffRecordPagerAdapter(getSupportFragmentManager());
        mNotesFragment = new MainActivityFragmentNotes();
        mChatsFragment = new MainActivityFragmentChats();
        mPeopleFragment = new MainActivityFragmentPeople();
        mPagerAdapter.addFragment(mNotesFragment, mNotesFragment.SECTION_NAME);
        mPagerAdapter.addFragment(mChatsFragment, "Chats");
        mPagerAdapter.addFragment(mPeopleFragment, "People");

        mViewPager.setAdapter(mPagerAdapter);

        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.UI, "onCreated called.");
    }

    @Override protected void onResume() {
        super.onResume();
        Log.d("SCREEN", "Keeping screen on for debugging.");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    public void onStart(){
        super.onStart();
        mGDriveModel.open();
    }

    @Override
    public void onStop() {
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.UI, "onStop() called");
        mGDriveModel.close();
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "onActivityResult called requestCode:" + requestCode + " result code:" + resultCode);
        if (requestCode==GoogleDriveModel.REQUEST_CODE_RESOLUTION) {
            if (mGDriveModel != null) {
                mGDriveModel.close();
            }
            mGDriveModel = new GoogleDriveModel(this);
        }
    }

    @Override
    protected void onDestroy(){
        JCLog.log(JCLog.LogLevel.VERBOSE, JCLog.LogAreas.UI, "onDestroy() called");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        //process fragment back stack first
        FragmentBackStackPressed currentFragment = (FragmentBackStackPressed) mPagerAdapter.getItem(mViewPager.getCurrentItem());
        if (!currentFragment.onBackPressed()) {
            int count = getFragmentManager().getBackStackEntryCount();
            if (count == 0) {
                super.onBackPressed();
                //additional code
            } else {
                getFragmentManager().popBackStack();
            }
        }
    }
}
