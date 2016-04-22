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
        ((MainActivity)getActivity()).mGDriveModel.writeTxtFile(mAssetID, mContent);
    }

    @Override
    public void onPause(){
        commitContent();
        super.onPause();
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}