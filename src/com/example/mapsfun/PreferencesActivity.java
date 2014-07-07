package com.example.mapsfun;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

//private String 
public class PreferencesActivity extends PreferenceActivity{
	@Override
	  public void onCreate(Bundle savedInstanceState) {
		 super.onCreate(savedInstanceState);

	        if(savedInstanceState == null)
	            getFragmentManager().beginTransaction()
	                .replace(android.R.id.content, new PrefFragment())
	                .commit();

	   
	  }
	
}
