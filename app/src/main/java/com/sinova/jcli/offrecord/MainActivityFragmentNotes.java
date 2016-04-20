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
public class MainActivityFragmentNotes extends Fragment {

    static int currentFragment=0;

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
        if (currentFragment==0) {
            transaction.replace(R.id.notes_child_fragment, new MainActivityFragmentNotesList()).commit();
        }else{
            transaction.replace(R.id.notes_child_fragment, new MainActivityFragmentNotesEdit()).commit();
        }

        return rootView;
    }

    @Override
    public void onStop(){
        super.onStop();
    }


}
