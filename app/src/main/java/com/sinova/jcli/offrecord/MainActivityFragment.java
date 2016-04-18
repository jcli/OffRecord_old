package com.sinova.jcli.offrecord;

import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.android.gms.drive.Metadata;

import java.util.Observable;
import java.util.Observer;
import java.util.zip.Inflater;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements Observer{

    private MetadataArrayAdapter mDriveAssetArrayAdapter;
    private MainActivity mMainActivity;

    private int mResource;
    private Context mContext;

    private class MetadataArrayAdapter extends ArrayAdapter<Metadata>{

        private int mScreenWidth;
        private int mScreenHeight;

        public MetadataArrayAdapter(Context context, int resource) {
            super(context, resource);
            mContext=context;
            mResource=resource;
            Display display = ((Activity) context).getWindowManager ().getDefaultDisplay ();
            Point size = new Point ();
            display.getSize (size);
            mScreenWidth= size.x;
            mScreenHeight = size.y;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            View row=inflater.inflate(mResource,parent,false);
            TextView title = (TextView)row.findViewById(R.id.list_item_drive_asset_title);
            Metadata data = (Metadata)getItem(position);
            title.setText(data.getTitle());
            ObjectAnimator.ofFloat(row,"alpha",0,1).setDuration(500).start();
            int startX= (int) (0.15*mScreenWidth);
            ObjectAnimator.ofFloat(row,"x",startX,0).setDuration(500).start();
            return row;
        }
    }

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mMainActivity = (MainActivity)getActivity();
        mDriveAssetArrayAdapter = new MetadataArrayAdapter(mMainActivity,
                R.layout.list_item_drive_asset);
        ListView driveAssetListView = (ListView) rootView.findViewById(R.id.driveAssetListView);
        driveAssetListView.setAdapter(mDriveAssetArrayAdapter);
        driveAssetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Metadata item = (Metadata)(parent.getAdapter().getItem(position));
                if (item.isFolder()){
                    mMainActivity.mGDriveModel.gotoFolderByTitle(item.getTitle());
                }
            }
        });


        final FloatingActionsMenu menuMultipleActions = (FloatingActionsMenu) rootView.findViewById(R.id.multiple_actions);
        final FloatingActionButton addFileButton = (FloatingActionButton) rootView.findViewById(R.id.action_add_file);
        addFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuMultipleActions.collapse();
            }
        });

        final FloatingActionButton addFolderButton = (FloatingActionButton) rootView.findViewById(R.id.action_add_folder);
        addFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuMultipleActions.collapse();
            }
        });

        return rootView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                //do whatever you want to do here.
                mMainActivity.mGDriveModel.popFolderStack();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart(){
        super.onStart();
        mMainActivity.mGDriveModel.addObserver(this);
        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "fragment added as observer...");
    }

    @Override
    public void update(Observable observable, Object data) {
        JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "change detected!!");
        mDriveAssetArrayAdapter.clear();
        Metadata items[]=mMainActivity.mGDriveModel.getCurrentFolder().items;
        if(items!=null) {
            mDriveAssetArrayAdapter.addAll(mMainActivity.mGDriveModel.getCurrentFolder().items);
        }
    }
}
