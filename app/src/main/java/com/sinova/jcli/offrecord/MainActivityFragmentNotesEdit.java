package com.sinova.jcli.offrecord;

import android.os.Bundle;
import android.provider.Contacts;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by jcli on 4/20/16.
 */
public class MainActivityFragmentNotesEdit extends Fragment implements FragmentBackStackPressed{
    String mContent="";
    String mAssetID;

    EditText mEditView;

    public MainActivityFragmentNotesEdit() {
    }

    public MainActivityFragmentNotesEdit(String assetID, String contentStr) {
        mContent = contentStr;
        mAssetID = assetID;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState!=null){
            mAssetID=savedInstanceState.getString("mAssetID");
            mContent=savedInstanceState.getString("mContent");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_note_edit, container, false);
        mEditView = (EditText)rootView.findViewById(R.id.note_file_editText);
        mEditView.setText(mContent, TextView.BufferType.EDITABLE);
        return rootView;
    }

    public void commitContent(){
        mContent = mEditView.getText().toString();
        ((MainActivity)getActivity()).mGDriveModel.writeTxtFile(mAssetID, mContent, new GoogleDriveModel.WriteTxtFileCallback() {
            @Override
            public void callback(boolean success) {
                if (success){
                    JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.GOOGLEAPI, "file write successful: "+mAssetID);
                }else{
                    JCLog.log(JCLog.LogLevel.ERROR, JCLog.LogAreas.GOOGLEAPI, "file write failed: "+mAssetID);
                }
            }
        });
    }

    @Override
    public void onPause(){
        commitContent();
        super.onPause();
    }

    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("mAssetID", mAssetID);
        state.putString("mContent", mContent);
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
