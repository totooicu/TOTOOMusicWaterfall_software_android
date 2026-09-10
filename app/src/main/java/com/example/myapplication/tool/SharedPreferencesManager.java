package com.example.myapplication.tool;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesManager {
    public static SharedPreferences pref;

    public static void Init(Activity context){
        if(pref==null)pref=context.getSharedPreferences("totoo", Context.MODE_PRIVATE);
    }
    public static void WriteJwt(String Authorization){
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("Authorization", Authorization); // Storing string
//        editor.apply();
        editor.commit();
    }
public static void WriteIpPort(String Authorization){
    SharedPreferences.Editor editor = pref.edit();
    editor.putString("IPPORT", Authorization); // Storing string
//        editor.apply();
    editor.commit();
}
    public static int  GetIntValue(String k,int defValue){
        return pref.getInt(k, defValue);
    }

    public static String  GetStrValue(String k,String defValue){
        return pref.getString(k, defValue);
    }
    public static String GetJwt(){
        return pref.getString("Authorization", ""); // getting String
    }
    public static String GetIpPort(){
        return pref.getString("IPPORT", "");
    }
}
