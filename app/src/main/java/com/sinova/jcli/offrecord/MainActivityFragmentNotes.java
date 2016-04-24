package com.sinova.jcli.offrecord;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by jcli on 4/20/16.
 */
public class MainActivityFragmentNotes extends Fragment implements FragmentBackStackPressed{

    static int currentFragment=0;
    MainActivityFragmentNotesList notesList;
    MainActivityFragmentNotesEdit notesEdit;

    public MainActivityFragmentNotes() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_notes, container, false);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
//        notesEdit = new MainActivityFragmentNotesEdit();
        if (notesList==null) {
            notesList = new MainActivityFragmentNotesList();
        }
        if (getChildFragmentManager().findFragmentById(R.id.notes_child_fragment)==null) {
//        if (currentFragment==0) {
            transaction.replace(R.id.notes_child_fragment, notesList).commit();
//        }else{
//            transaction.replace(R.id.notes_child_fragment, notesEdit).commit();
//        }
        }
        return rootView;
    }

    @Override
    public void onStop(){
        super.onStop();
    }


    @Override
    public boolean onBackPressed() {
        int childBackStackCount = getChildFragmentManager().getBackStackEntryCount();
        if (childBackStackCount>0) {
            getChildFragmentManager().popBackStack();
            JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.UI, "going back to asset list.");
            return true;
        }else {
            Fragment currentFragment = getChildFragmentManager().findFragmentById(R.id.notes_child_fragment);
            if (currentFragment==notesList && currentFragment!=null){
                JCLog.log(JCLog.LogLevel.INFO, JCLog.LogAreas.UI, "going up to parent folder.");
                notesList.goUpLevel();
                return true;
            }
            return false;
        }
    }
}
