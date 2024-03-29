package com.sinova.jcli.offrecord;

import android.util.Log;

import java.util.Calendar;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;

/**
 * Created by jcli on 4/8/16.
 */
public class JCLog {
    public enum LogAreas{
        UI("UI"),
        GOOGLEAPI("GOOGLEAPI");
        private String name;
        private LogAreas(String name){this.name = name;}
    }

    public enum LogLevel{
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARNING(3),
        ERROR(4);
        private int value;
        private LogLevel(int value){
            this.value=value;
        }
        public int getValue(){return this.value;}
    }
    private static EnumSet<LogAreas> mCurrentAreas=EnumSet.noneOf(LogAreas.class);
    private static LogLevel mCurrentLogLevel=LogLevel.VERBOSE;
    private static boolean mPrintThread=false;

    //public static void out()
    public static void enableLogArea(LogAreas newArea){
        if (!mCurrentAreas.contains(newArea)) {
            mCurrentAreas.add(newArea);
        }
//        printCurrentAreas();
    }

    public static void disableLogArea(LogAreas oldArea){
        if (mCurrentAreas.contains(oldArea)){
            mCurrentAreas.remove(oldArea);
        }
    }

    public static void clearAllLogAreas(){
        mCurrentAreas = EnumSet.noneOf(LogAreas.class);
    }

    public static void printCurrentAreas(){
        for (LogAreas area: mCurrentAreas){
            Log.v("JCLog", area.name);
        }
    }

    public static void log(LogLevel level, LogAreas area, String message){
        String threadInfo="";
        if (mPrintThread){
            threadInfo = ", thread(" + Thread.currentThread().getName() + ")";
        }
        if (mCurrentAreas.contains(area) && level.getValue()>= mCurrentLogLevel.getValue()) {
            Exception ex = new Exception();
            String callerClass = ex.getStackTrace()[1].getClassName();
            callerClass = callerClass.substring(callerClass.lastIndexOf('.')+1);
            String callerMethod = ex.getStackTrace()[1].getMethodName();
            String Tag = JCLog.class.getSimpleName() + ": " + callerClass + "::" + callerMethod + ", LogArea(" + area.name + ")" + threadInfo;
            switch (level){
                case VERBOSE:
                    Log.v(Tag, message);
                    break;
                case DEBUG:
                    Log.d(Tag, message);
                    break;
                case INFO:
                    Log.i(Tag, message);
                    break;
                case WARNING:
                    Log.w(Tag, message);
                    break;
                case ERROR:
                    Log.e(Tag, message);
                    break;
                default:
                    break;
            }
        }
    }
}
