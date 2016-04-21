package com.sinova.jcli.offrecord;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.android.gms.drive.Metadata;

import java.util.Observable;
import java.util.Observer;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragmentNotesList extends Fragment implements Observer, FragmentBackStackPressed, GoogleDriveModel.GoogleDriveModelCallbacks {

    private MetadataArrayAdapter mDriveAssetArrayAdapter;
    private MainActivity mMainActivity;

    @Override
    public boolean onBackPressed() {
        return false;
    }

    private class MetadataArrayAdapter extends ArrayAdapter<Metadata>{

        private int mResource;
        private Context mContext;
        private int mScreenWidth;
        private int mScreenHeight;

        public MetadataArrayAdapter(Context context, int resource) {
            super(context, resource);
            mContext=context;
            mResource=resource;
            Display display = ((Activity) context).getWindowManager().getDefaultDisplay ();
            Point size = new Point();
            display.getSize(size);
            mScreenWidth = size.x;
            mScreenHeight = size.y;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            View row=inflater.inflate(mResource,parent,false);
            TextView title = (TextView)row.findViewById(R.id.list_item_drive_asset_title);
            TextView type = (TextView) row.findViewById(R.id.list_item_drive_asset_type);
            Metadata data = (Metadata)getItem(position);
            title.setText(data.getTitle());
            if (data.isFolder()){
                type.setText("Folder");
            }else{
                type.setText("Text File");
            }
            ObjectAnimator.ofFloat(row,"alpha",0,1).setDuration(500).start();
            int startX= (int) (0.15*mScreenWidth);
            ObjectAnimator.ofFloat(row,"x",startX,0).setDuration(500).start();
            return row;
        }
    }

    public MainActivityFragmentNotesList() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_note_list, container, false);
        mMainActivity = (MainActivity)getActivity();
        mMainActivity.mGDriveModel.setCallbackReceiver(this);
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
                }else{
                    // open file
                    mMainActivity.mGDriveModel.openReadTxtFile(item.getDriveId().encodeToString());
                }
            }
        });

        // populate the list
        mDriveAssetArrayAdapter.clear();
        if (mMainActivity.mGDriveModel.getCurrentFolder()!=null &&
                mMainActivity.mGDriveModel.getCurrentFolder().items!=null){
            mDriveAssetArrayAdapter.addAll(mMainActivity.mGDriveModel.getCurrentFolder().items);
        }

        final FloatingActionsMenu menuMultipleActions = (FloatingActionsMenu) rootView.findViewById(R.id.multiple_actions);
        final FloatingActionButton addFileButton = (FloatingActionButton) rootView.findViewById(R.id.action_add_file);
        addFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuMultipleActions.collapse();
                nameInputPopup("File Name", false);
            }
        });

        final FloatingActionButton addFolderButton = (FloatingActionButton) rootView.findViewById(R.id.action_add_folder);
        addFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menuMultipleActions.collapse();
                nameInputPopup("Folder Name", true);
            }
        });

        return rootView;
    }

    public void goUpLevel(){
        mMainActivity.mGDriveModel.popFolderStack();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                goUpLevel();
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
    public void onStop(){
        mMainActivity.mGDriveModel.deleteObserver(this);
        super.onStop();
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

    //////////////// private helper functions /////////////////

    private void nameInputPopup(final String title, final boolean isFolder){
        AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
        builder.setTitle(title);

        // Set up the input
        final EditText input = new EditText(mMainActivity);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString();
                boolean status;
                if (isFolder){
                    status = mMainActivity.mGDriveModel.createFolder(name, false);
                }else {
                    status = mMainActivity.mGDriveModel.createTxtFile(name, null);
                }
                if (!status){
                    nameInputPopup(title, isFolder);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    //////////// Google Drive model callback
    @Override
    public void txtFileContentAvaliable(String assetID, String contentStr) {
        // launch the edit fragment
        FragmentTransaction transaction = getParentFragment().getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.notes_child_fragment, new MainActivityFragmentNotesEdit(assetID, contentStr)).addToBackStack(null).commit();
    }

    @Override
    public void fileCommitComplete(String assetID) {

    }

}
