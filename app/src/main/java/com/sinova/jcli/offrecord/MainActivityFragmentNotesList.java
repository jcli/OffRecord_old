package com.sinova.jcli.offrecord;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.EdgeEffectCompat;
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
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Metadata;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragmentNotesList extends Fragment implements Observer, FragmentBackStackPressed {

    private MetadataArrayAdapter mDriveAssetArrayAdapter;
    private MainActivity mMainActivity;

    private GoogleDriveModel.FolderInfo mCurrentFolder;
    private String mRestoredFolderIDStr;
    private String mSectionRootIDStr;
    private Map<Integer, String> mCurrentSelections;
    private View mFragmentView;

    // floating buttons
    private FloatingActionsMenu2 mMenuMultipleActions;
    private FloatingActionButton mAddFileButton;
    private FloatingActionButton mAddFolderButton;
    private FloatingActionButton mSelectItemsButton;
    private FloatingActionButton mDeleteSelectionButton;

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
        JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.UI, "onCreate called");
        if (savedInstanceState!=null){
            mRestoredFolderIDStr = savedInstanceState.getString("currentFolder");
        }else{
            mRestoredFolderIDStr=null;
        }
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_note_list, container, false);
        mMainActivity = (MainActivity)getActivity();
        mDriveAssetArrayAdapter = new MetadataArrayAdapter(mMainActivity,
                R.layout.list_item_drive_asset);
        ListView driveAssetListView = (ListView) rootView.findViewById(R.id.driveAssetListView);
        driveAssetListView.setAdapter(mDriveAssetArrayAdapter);
        driveAssetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mCurrentSelections==null) {
                    itemAction(parent, view, position, id);
                }else{
                    itemSelection(parent, view, position, id);
                }
            }
        });

        mMenuMultipleActions = (FloatingActionsMenu2) rootView.findViewById(R.id.multiple_actions);
        setupFloatingButtons();

        // try to populate the list
        if (mMainActivity.mGDriveModel.isConnected()){
            showInitialList();
        }

        mFragmentView=rootView;
        return rootView;
    }

    public boolean goUpLevel(){
        if (mCurrentFolder.parentFolder!=null && !mSectionRootIDStr.equals(mCurrentFolder.folder.getDriveId().encodeToString())) {
            mMainActivity.mGDriveModel.listFolderByID(mCurrentFolder.parentFolder.getDriveId().encodeToString(),
                    listFolderByIDCallback);
            return true;
        }else{
            return false;
        }
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
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (mCurrentFolder!=null) {
            state.putString("currentFolder", mCurrentFolder.folder.getDriveId().encodeToString());
        }
    }

    @Override
    public void update(Observable observable, Object data) {
        showInitialList();
    }

    //////////////// private helper functions /////////////////
    private void setupFloatingButtons(){
        mAddFileButton = new FloatingActionButton(mMainActivity);
        mAddFileButton.setTitle("Add File");
        mAddFileButton.setColorNormal(ContextCompat.getColor(mMainActivity, R.color.white));
        mAddFileButton.setColorPressed(ContextCompat.getColor(mMainActivity, R.color.white_pressed));
        mAddFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMenuMultipleActions.collapse();
                nameInputPopup("File Name", false);
            }
        });

        mAddFolderButton = new FloatingActionButton(mMainActivity);
        mAddFolderButton.setTitle("Add Folder");
        mAddFolderButton.setColorNormal(ContextCompat.getColor(mMainActivity, R.color.white));
        mAddFolderButton.setColorPressed(ContextCompat.getColor(mMainActivity, R.color.white_pressed));
        mAddFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMenuMultipleActions.collapse();
                nameInputPopup("Folder Name", true);
            }
        });

        mSelectItemsButton = new FloatingActionButton(mMainActivity);
        final String defaultTitle="Select Items";
        final String cancelTitle="Cancel Selections";
        mSelectItemsButton.setTitle(defaultTitle);
        mSelectItemsButton.setColorNormal(ContextCompat.getColor(mMainActivity, R.color.white));
        mSelectItemsButton.setColorPressed(ContextCompat.getColor(mMainActivity, R.color.white_pressed));
        mSelectItemsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMenuMultipleActions.collapse();
                if (mCurrentSelections==null){
                    // go into selection mode
                    mCurrentSelections=new HashMap<Integer, String>();
                    mSelectItemsButton.setTitle(cancelTitle);
                    populateSelectionButtons();
                }else{
                    // cancel all selection
                    mCurrentSelections.clear();
                    mCurrentSelections=null;
                    mSelectItemsButton.setTitle(defaultTitle);
                    mDriveAssetArrayAdapter.notifyDataSetChanged();
                    populateDefalutButtons();
                }
            }
        });

        mDeleteSelectionButton = new FloatingActionButton(mMainActivity);
        mDeleteSelectionButton.setTitle("Delete Selections");
        mDeleteSelectionButton.setColorNormal(ContextCompat.getColor(mMainActivity, R.color.white));
        mDeleteSelectionButton.setColorPressed(ContextCompat.getColor(mMainActivity, R.color.white_pressed));
        mDeleteSelectionButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mMenuMultipleActions.collapse();
                mSelectItemsButton.setTitle(defaultTitle);
                // delete selections
                if (!mCurrentSelections.isEmpty()) {
                    Deque<String> itemIDs = new ArrayDeque<String>();
                    for (String value : mCurrentSelections.values()) {
                        itemIDs.push(value);
                    }
                    mMainActivity.mGDriveModel.deleteMultipleItems(itemIDs, new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            if (status.isSuccess()) {
                                JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.UI, "multiple items deleted.");
                            }
                            mMainActivity.mGDriveModel.listFolderByID(mCurrentFolder.folder.getDriveId().encodeToString(), listFolderByIDCallback);
                        }
                    });
                    // clear current selections
                    mCurrentSelections.clear();
                }else{
                    mDriveAssetArrayAdapter.notifyDataSetChanged();
                }
                mCurrentSelections=null;
                populateDefalutButtons();
            }
        });

        if (mCurrentSelections==null){
            JCLog.log(JCLog.LogLevel.WARNING, JCLog.LogAreas.UI, "adding buttons");
            populateDefalutButtons();
        }else{
            populateSelectionButtons();
        }
    }

    private void populateDefalutButtons(){
        mMenuMultipleActions.removeAllButtons();
        mMenuMultipleActions.addButton(mSelectItemsButton);
        mMenuMultipleActions.addButton(mAddFileButton);
        mMenuMultipleActions.addButton(mAddFolderButton);
    }

    private void populateSelectionButtons(){
        mMenuMultipleActions.removeAllButtons();
        mMenuMultipleActions.addButton(mSelectItemsButton);
        mMenuMultipleActions.addButton(mDeleteSelectionButton);
    }

    private void itemAction(AdapterView<?> parent, View view, int position, long id){
        Metadata item = (Metadata)(parent.getAdapter().getItem(position));
        if (item.isFolder()){
            mMainActivity.mGDriveModel.listFolderByID(item.getDriveId().encodeToString(), listFolderByIDCallback);
        }else{
            // open file
            final String assetID = item.getDriveId().encodeToString();
            mMainActivity.mGDriveModel.readTxtFile(assetID, new GoogleDriveModel.ReadTxtFileCallback() {
                @Override
                public void callback(String fileContent) {
                    // launch the edit fragment
                    FragmentTransaction transaction = getParentFragment().getChildFragmentManager().beginTransaction();
                    transaction.replace(R.id.notes_child_fragment, new MainActivityFragmentNotesEdit(assetID, fileContent)).addToBackStack(null).commit();
                }
            });
        }
    }

    private void itemSelection(AdapterView<?> parent, View view, int position, long id){
        Metadata item = (Metadata)(parent.getAdapter().getItem(position));
        if (mCurrentSelections.containsKey(position)
                && mCurrentSelections.get(position).equals(item.getDriveId().encodeToString())){
                //clear selection
                mCurrentSelections.remove(position);
            view.setBackgroundColor(ContextCompat.getColor(mMainActivity, R.color.white));
        }else {
            mCurrentSelections.put(position, item.getDriveId().encodeToString());
            view.setBackgroundColor(ContextCompat.getColor(mMainActivity, R.color.pink));
        }
    }

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
                boolean status=true;
                if (isFolder){
                    mMainActivity.mGDriveModel.createFolderInFolder(name, mCurrentFolder.folder.getDriveId().encodeToString(),
                            false, listFolderByIDCallback);
                }else {
                    mMainActivity.mGDriveModel.createTxtFile(name, mCurrentFolder.folder.getDriveId().encodeToString(), listFolderByIDCallback);
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

    private void showInitialList(){
        if (mCurrentFolder!=null){
            mDriveAssetArrayAdapter.clear();
            mDriveAssetArrayAdapter.addAll(mCurrentFolder.items);
        }else if (mRestoredFolderIDStr!=null){
            mMainActivity.mGDriveModel.listFolderByID(mRestoredFolderIDStr, listFolderByIDCallback);
        }else{
            mMainActivity.mGDriveModel.listSectionRoot(MainActivityFragmentNotes.SECTION_NAME, listFolderByIDCallback);
        }
        mMainActivity.mGDriveModel.listSectionRoot(MainActivityFragmentNotes.SECTION_NAME, new GoogleDriveModel.ListFolderByIDCallback() {
            @Override
            public void callback(GoogleDriveModel.FolderInfo info) {
                if (info!=null){
                    mSectionRootIDStr=info.folder.getDriveId().encodeToString();
                }
            }
        });
    }

    //////////// callbacks /////////////

    private GoogleDriveModel.ListFolderByIDCallback listFolderByIDCallback = new GoogleDriveModel.ListFolderByIDCallback() {
        @Override
        public void callback(GoogleDriveModel.FolderInfo info) {
            if (info!=null) {
                mCurrentFolder = info;
                mDriveAssetArrayAdapter.clear();
                mDriveAssetArrayAdapter.addAll(info.items);
            }
        }
    };

}
