package com.chchchChia.SimpleGPS;


import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import com.chchchChia.SimpleGPS.R;

public class PrefFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener{
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        getPreferenceScreen().getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(this);
    }

	@Override
	public void onResume() {
	    super.onResume();
	    getPreferenceScreen().getSharedPreferences()
	            .registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
	    super.onPause();
	    getPreferenceScreen().getSharedPreferences()
	            .unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("Speed units")) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            connectionPref.setSummary(sharedPreferences.getString(key, ""));
            MainOSD.speedUnit=sharedPreferences.getString(key, "m/s");
            GPSservice.speedUnit=sharedPreferences.getString(key, "m/s");
        }
		if (key.equals("Distance units")) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            connectionPref.setSummary(sharedPreferences.getString(key, ""));
            MainOSD.distUnit=sharedPreferences.getString(key, "m");
            GPSservice.distUnit=sharedPreferences.getString(key, "m");
            if(MainOSD.distUnit.equals("mi")){
            	MainOSD.errorUnit="ft";
            }else{
            	MainOSD.errorUnit="m";
            }
        }
		/*
		if (key.equals("debugMode")) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            //sharedPreferences.getBoolean("debugMode", false);
            MainOSD.debugMode=sharedPreferences.getBoolean("debugMode", false);         
        }*/
		if(key.equals("timePref")){
			Preference connectionPref = findPreference(key);
			connectionPref.setSummary(sharedPreferences.getString(key, ""));
			if(sharedPreferences.getString(key, "TotTime").equals("MoveTime")){
			MainOSD.onlyTimeMoving=true;
			GPSservice.onlyTimeMoving=true;
			}else{
				MainOSD.onlyTimeMoving=false;
				GPSservice.onlyTimeMoving=false;
			}
		}

		
	}
}
