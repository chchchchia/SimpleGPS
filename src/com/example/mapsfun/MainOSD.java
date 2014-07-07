package com.example.mapsfun;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.os.Build;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainOSD extends Activity {
	private double speed, distance, latitude, longitude, error, avgSpeed;
	private int timeTaskLoopCount;
	private Location location;
	private static final String TAG="MainOSD";
	protected static String distUnit="m";
	protected static String speedUnit="m/s";
	protected static String errorUnit="m";
	protected static boolean debugMode=false;
	protected static boolean onlyTimeMoving;
	private long timeRunning;
	private Button btnBeginGPS,btnStopGPS,btnMap,btnReset;
	private TextView textLat,textLong,textDistance,textSpeed,textError,textAvgSpeed,textTime;
	private Intent mIntent;
	private IntentFilter filter;
	private Context context;
	private boolean gpsEnabled, chronoRunning;
	private boolean positionChanged=false;
	private ArrayList<Location> overlayArray;
	private GPSservice gpsServ= new GPSservice();
	DecimalFormat df = new DecimalFormat("####0.00");

	//TODO dynamically update Avgspeed
	
	@SuppressLint("NewApi")
    private GoogleMap googleMap;
 
    @Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 
        chronoRunning=false;
        overlayArray=new ArrayList<Location>();
        timeTaskLoopCount=0;
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(this);
        speedUnit=prefs.getString("Speed units", "m/s");
        distUnit=prefs.getString("Distance units", "m");
        //debugMode=prefs.getBoolean("debugMode", false);
        onlyTimeMoving=(prefs.getString("timePref", "TotTime").equals("MoveTime") ? true:false);
        if(distUnit.equals("mi")){
        	errorUnit="ft";
        }else{
        	errorUnit="m";
        }
        context=this;
        setContentView(R.layout.activity_main);
        initGUI();
        mIntent = new Intent(this, GPSservice.class).setAction("POSITION_UPDATE");
        filter= new IntentFilter();
        filter.addAction("POSITION_UPDATE");
        filter.addAction("TIME_UPDATE");
        
        if (savedInstanceState != null ){
        	if (savedInstanceState.getDouble("distance")==0){
        		bundleReadFromService();
        	}
        	overlayArray=savedInstanceState.getParcelableArrayList("overlayArray");
        	speed=savedInstanceState.getDouble("currentSpeed",0);
    		latitude=savedInstanceState.getDouble("latitude",0);
    		longitude=savedInstanceState.getDouble("longitude",0);
    		distance=savedInstanceState.getDouble("distance",0);
    		error=savedInstanceState.getDouble("error", 0);
    		gpsEnabled=savedInstanceState.getBoolean("gpsEnabled");
    		avgSpeed=savedInstanceState.getDouble("avgSpeed", 0);
    		chronoRunning=savedInstanceState.getBoolean("chronoRunning", false);
    		updateGUI();
        }
        
        if(isGPSstarted()){//If the activity is destroyed while the service is still active
			btnBeginGPS.setEnabled(false);
 	        btnStopGPS.setEnabled(true);
 	        gpsEnabled=true;
 	       LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
           	      filter);
 	       if(savedInstanceState==null){
 	    	   Bundle update=GPSservice.update;
 	    	   distance=update.getDouble("distance");
 	    	   this.location=update.getParcelable("location");
 	    	   speed=location.getSpeed();
 	    	   latitude=location.getLatitude();
 	    	   longitude=location.getLongitude();
 	    	   error=location.getAccuracy();
 	    	   avgSpeed=update.getDouble("avgSpeed");
 	    	   
 	    	   
 	    	   updateGUI();
 	       }	       
		}
    }
 
    private void bundleReadFromService(){
    	Bundle update=gpsServ.getUpdateBundle();
    	if(update!=null){
	  	   distance=update.getDouble("distance");
	  	   if(update.getParcelable("location")!=null){
	  		   this.location=update.getParcelable("location");
		  	   speed=location.getSpeed();
		  	   latitude=location.getLatitude();
		  	   longitude=location.getLongitude();
		  	   error=location.getAccuracy();
	  	   }
    	}
    }
    private boolean isGPSstarted(){
    	return GPSservice.SERVICE_RUNNING;
    	
    }
    private void initGUI(){
    	btnBeginGPS=(Button)findViewById(R.id.btnBeginGPS);
    	btnBeginGPS.setOnClickListener(mGpsBegin);
    	btnStopGPS=(Button)findViewById(R.id.btnStopGPS);
    	btnStopGPS.setOnClickListener(mGpsStop);
    	btnStopGPS.setEnabled(false);
    	btnReset=(Button)findViewById(R.id.btnReset);
    	btnReset.setOnClickListener(mResetGUI);
    	btnMap=(Button)findViewById(R.id.btnMap);
    	btnMap.setOnClickListener(mMap); 
    	textTime=(TextView)findViewById(R.id.textTime);
    	textLat=(TextView)findViewById(R.id.textLat);
    	textLong=(TextView)findViewById(R.id.textLong);
    	textDistance=(TextView)findViewById(R.id.textDist);
    	textSpeed=(TextView)findViewById(R.id.textSpeed);
    	textError=(TextView)findViewById(R.id.textError);
    	textAvgSpeed=(TextView)findViewById(R.id.textAvgSpeed);
    }
    
    private void updateGUI(){
    	//TODO this should be divied up into one fxn for the clock, one for everything else;
    	textSpeed.setText(unitConverter_Speed(speed));
 		textLat.setText("Lat="+Double.toString(latitude));
 		textLong.setText("Long="+Double.toString(longitude));
 		textDistance.setText(unitConverter_Distance(distance));
 		textError.setText("error="+unitConverter_Error(error));
 		textAvgSpeed.setText("Avg Speed= "+unitConverter_Speed(this.avgSpeed));
 		textTime.setText(formatTimeFromMS(timeRunning));
 		if(isGPSstarted()){//If the activity is destroyed while the service is still active
			btnBeginGPS.setEnabled(false);
 	        btnStopGPS.setEnabled(true);
 	        gpsEnabled=true;
 		}
    }
    
    private String unitConverter_Speed(double speed){
    	switch(speedUnit){
    	case "m/s": return df.format(speed)+" m/s";
    	case "km/hr": return df.format(speed*60*60/1000)+" km/hr";
    	case "mph": return df.format(speed*60*60/1609.34)+" mph";
    	default: return df.format(speed)+" m/s";
    	}	
    }
    
    private String unitConverter_Distance(double dist){
    	switch(distUnit){
    	case "km": return df.format(dist/1000)+" km";
    	case "mi": return df.format(dist/1609.34)+" mi";
    	default: return df.format(dist)+" m";
    	}
    }
    
    private String unitConverter_Error(double dist){
    	switch(errorUnit){
    	case "m": return df.format(dist)+" m";
    	case "ft": return df.format(dist*3.28084)+" ft";
    	default: return df.format(dist)+" m";
    	}
    }
    
 // Define the callback for what to do when data is received
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.d(TAG,"Received Broadcast = "+ intent.getAction());
        	if(intent.getAction().equals(filter.getAction(0))){
        		distance=intent.getDoubleExtra("distance",0);
        		Location temp=(Location)intent.getParcelableExtra("location");
        		location=temp;
        		speed=temp.getSpeed();
        		latitude=temp.getLatitude();
        		longitude=temp.getLongitude();
        		error=temp.getAccuracy();
        		avgSpeed=intent.getDoubleExtra("avgSpeed", 0);
        		positionChanged=true;
        		updateGUI();
        	}
        	if(intent.getAction().equals(filter.getAction(1))){
        		timeRunning=intent.getLongExtra("timeRunning", timeRunning);
        		textTime.setText(formatTimeFromMS(timeRunning));
        	}
        }
    };
    
    
    
    public static void setSpeedUnit(String s){
    	speedUnit=s;
    }
    private OnClickListener mGpsBegin=new OnClickListener(){
    	 public void onClick(View v){
    		 LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
    		 if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){//if gps is not enabled, inform user and provide a shortcut to sys settings menu
    			 AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context)
				.setTitle("GPS is Disabled!")
				.setMessage("GPS needs to be enabled for location tracking. Please Click settings and enable GPS to continue.")
				.setCancelable(false)
				.setPositiveButton("Settings",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						Intent gpsSettings= new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
						startActivity(gpsSettings);
					}
				  })
				  .setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						dialog.cancel();		
					}
				  });
			AlertDialog alertDialog=alertDialogBuilder.create();
			alertDialog.show();
 		}else{
 			btnBeginGPS.setEnabled(false);
 	        btnStopGPS.setEnabled(true);
 			startService(mIntent);
 			gpsEnabled=true;
 			chronoRunning=true;
 			gpsServ.setChronoRunning(true);
 			LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(receiver,
       	      filter);
 		}
         }
    };
    
    private OnClickListener mResetGUI=new OnClickListener(){
    	public void onClick(View v){
    		distance=0;
    		latitude=0;
    		longitude=0;
    		speed=0;
    		error=0;
    		avgSpeed=0;
    		timeRunning=0;
    		//overlayArray.clear(); <-- this only sets each element to null, it does NOT resize the array
    		overlayArray=new ArrayList<Location>();
    		gpsServ.reset();
    		updateGUI();
    	}
    };
    
    private String formatTimeFromMS(long time){
    	String temp = String.format("%02d:%02d:%02d", 
    			TimeUnit.MILLISECONDS.toHours(time),
    			TimeUnit.MILLISECONDS.toMinutes(time) -  
    			TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)), 
    			TimeUnit.MILLISECONDS.toSeconds(time) - 
    			TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)));  
    	return temp;
    }
    private OnClickListener mGpsStop=new OnClickListener(){
   	 public void onClick(View v){
        btnBeginGPS.setEnabled(true);
        btnStopGPS.setEnabled(false);
        Bundle temp = gpsServ.getUpdateBundle();
        	overlayArray=temp.getParcelableArrayList("overlayArray");
        	 Log.d("Map button overlay Array=",overlayArray.toString());
        gpsEnabled=false;
        chronoRunning=false;
        gpsServ.setChronoRunning(false);
        stopService(mIntent);
        LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(receiver);
        Toast.makeText(getBaseContext(), "GPS Tracking Stopped", Toast.LENGTH_SHORT).show();
        }
   };
    
   private OnClickListener mMap=new OnClickListener(){
	   public void onClick(View v){
		   Log.d("Map button overlay Array=",overlayArray.toString());
		   startActivityForResult(new Intent(context, OnScreenMap.class).putExtra("location", location).putExtra("distance",distance)
				   .putExtra("debugMode", debugMode).putExtra("overlayArray", overlayArray),1); 
	   }
   };
   
	
	@Override
	protected void onStop(){
		Log.d("OnStop","called");
		super.onStop();
	}
	
    @Override
    protected void onResume() {
    	if(this.gpsServ==null){
    	this.gpsServ=new GPSservice();
    	}
    	bundleReadFromService();
    	updateGUI();
        filter= new IntentFilter();
        filter.addAction("POSITION_UPDATE");
        filter.addAction("TIME_UPDATE"); 
        LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(receiver, filter);
        Log.d("onResume","called");
        super.onResume();
    }
    
		@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		}
		if(id==R.id.save_Route){
			boolean result=gpsServ.save();
			if (result){
				Toast.makeText(this, "Route Saved", Toast.LENGTH_SHORT).show();
			}else{
				Toast.makeText(this, "Could not save route!", Toast.LENGTH_SHORT).show();
			}
		}
		if(id==R.id.load_Route){
			boolean result=gpsServ.load();
			if (result){
				Toast.makeText(this, "Route loaded", Toast.LENGTH_SHORT).show();
			}else{
				Toast.makeText(this, "Could not load route!", Toast.LENGTH_SHORT).show();
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle state) {
	    state.putDouble("latitude", latitude);
	    state.putDouble("longitude", longitude);
	    state.putDouble("currentSpeed", speed);
	    state.putDouble("distance", distance);
	    state.putDouble("error", error);
	    state.putBoolean("gpsEnabled", gpsEnabled);
	    state.putBoolean("chronoRunning",chronoRunning);
	    state.putDouble("avgSpeed", avgSpeed);
	    state.putLong("timeRunning", timeRunning);
	    super.onSaveInstanceState(state);
	}


	@Override
	public void onPause(){
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onPause();
	}


	@Override
	public void onDestroy(){
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onDestroy();
	}
	

	@Override
	public void onActivityResult(int requestCode,int resultCode,Intent data){
		Log.d("request code, result code",requestCode+" "+ resultCode);
		if(isGPSstarted()){//If the activity is destroyed while the service is still active
			btnBeginGPS.setEnabled(false);
 	        btnStopGPS.setEnabled(true);
 	        gpsEnabled=true;
 	       LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
           	      filter);
        }
		if (requestCode==1){
			if(resultCode==0){
				if(data!=null){
					distance=data.getDoubleExtra("distance",0);
					Location temp=data.getParcelableExtra("location");
					if(temp!=null){
						speed=temp.getSpeed();
						latitude=temp.getLatitude();
						longitude=temp.getLongitude();
						error=temp.getAccuracy();
						updateGUI();
					}
				}
				if(isGPSstarted()){//If the activity is destroyed while the service is still active
					btnBeginGPS.setEnabled(false);
		 	        btnStopGPS.setEnabled(true);
		 	        gpsEnabled=true;
		 	       LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
		           	      filter);
		        }
			}
		}
	}
	
	//Preferences
	public static class SettingsFragment extends PreferenceFragment {
	    @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);

	        // Load the preferences from an XML resource
	        addPreferencesFromResource(R.xml.preferences);
	    }
	    
	}

	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			return rootView;
		}
	}

}
