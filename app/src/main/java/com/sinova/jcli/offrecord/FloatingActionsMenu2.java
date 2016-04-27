package com.sinova.jcli.offrecord;

import android.content.Context;
import android.util.AttributeSet;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jcli on 4/27/16.
 */
public class FloatingActionsMenu2 extends FloatingActionsMenu {

    private List<FloatingActionButton> currentButtons;

    public FloatingActionsMenu2(Context context) {
        super(context);
        currentButtons = new ArrayList<FloatingActionButton>();
    }

    public FloatingActionsMenu2(Context context, AttributeSet attrs) {
        super(context, attrs);
        currentButtons = new ArrayList<FloatingActionButton>();
    }

    public FloatingActionsMenu2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        currentButtons = new ArrayList<FloatingActionButton>();
    }

    @Override
    public void addButton(FloatingActionButton button) {
        currentButtons.add(button);
        super.addButton(button);
    }

    public void removeAllButtons(){
        for (FloatingActionButton button: currentButtons){
            super.removeButton(button);
        }
        currentButtons.clear();
    }
}
