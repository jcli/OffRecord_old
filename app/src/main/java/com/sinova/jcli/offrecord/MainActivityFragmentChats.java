package com.sinova.jcli.offrecord;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by jcli on 4/19/16.
 */
public class MainActivityFragmentChats extends Fragment implements FragmentBackStackPressed{

    public MainActivityFragmentChats() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_chats, container, false);
        return rootView;
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
