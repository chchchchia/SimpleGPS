package com.chchchChia.SimpleGPS;
import java.util.ArrayList;
import java.util.List;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import com.chchchChia.SimpleGPS.R;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

public class OnScreenMap extends FragmentActivity{
	private static final String TAG="OnScreenMap";
	private GoogleMap mGmap;
	private CameraPosition cameraPos;
	final LatLngBounds.Builder builder = new LatLngBounds.Builder();
	private ArrayList<Location> overlayArray=new ArrayList<Location>();
	private Intent mIntent;
	private IntentFilter filter;
	private double speed, distance;
	private Location currentLoc;
	private Marker mapLoc;
	private Polyline line;
	private boolean debugMode=false;
	private int markerCount=0;
	private GPSservice gpsServ;

	//TODO Save Route
	//TODO Load Route
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		gpsServ=new GPSservice();		
		mIntent = getIntent();
		setContentView(R.layout.map_frag);
        filter= new IntentFilter();
        filter.addAction("POSITION_UPDATE");  
		//If there's a savedBundle, and use that to recover values specific to map disp
        if (savedInstanceState != null ){
    		markerCount=savedInstanceState.getInt("markerCount",32);
    		cameraPos=savedInstanceState.getParcelable("cameraPos");
        } 
        getCurrentLocOnStartup();
        initilizeMap();

        //and lastly, register the receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
            	      filter);
	}
	
	private void getCurrentLocOnStartup(){
		if(GPSservice.SERVICE_RUNNING){
			Bundle temp=gpsServ.getUpdateBundle();
			if (temp!=null&&temp.getParcelable("location")!=null){
				speed=temp.getDouble("speed");
				distance=temp.getDouble("distance");
				overlayArray=temp.getParcelableArrayList("overlayArray");
				currentLoc=temp.getParcelable("location");
				//Log.d(TAG, "got update bundle in OSMap");
				//Log.d(TAG,"loc is "+currentLoc.toString());
				return;//because gps is the bestest
			}
        }
		//If no current gps loc is available, get the one passed in from OSD activity
        if(mIntent!=null&&mIntent.getParcelableExtra("location")!=null){//mIntent nullness can occur if the act is restarted after being destroyed
        	currentLoc=mIntent.getParcelableExtra("location");
        	distance=mIntent.getDoubleExtra("distance", 0);
        	overlayArray=mIntent.getParcelableArrayListExtra("overlayArray");
        	//Log.d(TAG, "got OSMap location from intent");
        	//Log.d(TAG,"loc is "+currentLoc.toString());
        	return;
        }else{
        	//if that's nobueno (ie, no fix was ever made since app start), get the last known pos
        	LocationManager locationManager=(LocationManager) this.getSystemService(LOCATION_SERVICE);
        	if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            	currentLoc=locationManager
                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
            	Log.d(TAG,"got OSMap location from gps-last");
            	Log.d(TAG,"loc is "+currentLoc.toString());
        	}
        else if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)&&currentLoc==null){
            	currentLoc=locationManager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            	Log.d(TAG,"got OSMap location from network-last");
            	Log.d(TAG,"loc is "+currentLoc.toString());
        }
            Log.d(TAG,"secondary fallback loc "+currentLoc.toString());
            
       	}
        if(currentLoc==null){//if, after all of that, the location is null...
        			//this fallback is for when the device does not have network or gps access
        			//and has never retrieved a location, so set it to the geographic center of the USA
        			//because...FREEDOM
        	currentLoc=new Location("PATRIOTISM");
        	currentLoc.setLatitude(39.83333333);
        	currentLoc.setLongitude(-98.5833333333);
        }
	}
	@Override
    protected void onResume() {
		initilizeMap();
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
	      	      filter);
		 super.onResume();
	}
	
	@Override
	public void onPause(){
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		Intent data = new Intent();
		 data.putExtra("distance", distance);
		 data.putExtra("currentLoc",currentLoc);
		setResult(1,data);
		super.onPause();
	}
	
	@Override
	protected void onStop(){
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		super.onStop();
	}
	@Override
	public void onDestroy(){
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
		Intent data = new Intent();
		 data.putExtra("distance", distance);
		 data.putExtra("currentLoc",currentLoc);
		 setResult(1,data);
		 finish();
		super.onDestroy();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle state) {
	    super.onSaveInstanceState(state);
	    state.putDouble("distance", distance);
	    state.putParcelable("cameraPos", mGmap.getCameraPosition());
	    state.putParcelable("currentLoc", currentLoc);
	    state.putInt("markerCount", markerCount);
	}

	 private BroadcastReceiver receiver = new BroadcastReceiver() {
		 double latitude;
		 double longitude;
		 double error;
	        @Override
	        public void onReceive(Context context, Intent intent) {
	        	if(intent.getAction().equals(filter.getAction(0))){
	        		//TODO if you end up not needing these local vars, delete them
	        		Location temp=intent.getParcelableExtra("location");
	        		speed=temp.getSpeed();
	        		latitude=temp.getLatitude();
	        		longitude=temp.getLongitude();
	        		distance=intent.getDoubleExtra("distance",0);
	        		error=temp.getAccuracy();
	        		overlayArray.add(temp);
	        		currentLoc=temp;
	        		//Log.d(TAG, "Update received = "+Double.toString(temp.getLongitude()));
	        		if(debugMode&&markerCount<=32){
	        			mGmap.addMarker(new MarkerOptions().position(new LatLng(latitude,longitude))
	        				.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_x)));
	        			mGmap.addCircle(new CircleOptions().center(new LatLng(latitude,longitude)).radius(error).strokeWidth(2));
	        			markerCount++;
	        		}else{
	        			mapLoc.setPosition(new LatLng(latitude,longitude));
	        			if (line!=null){
	        				List<LatLng> ptList=line.getPoints();
	        				ptList.add(new LatLng(latitude,longitude));
	        				line.setPoints(ptList);
	        			}else{
	        				line=mGmap.addPolyline(new PolylineOptions().add(new LatLng(latitude,longitude)));
	        			}
	        		}
	        		mGmap.moveCamera(CameraUpdateFactory.newLatLngZoom(
							new LatLng(latitude,longitude),mGmap.getCameraPosition().zoom));
	        	}
	        }
	    };
	    

	private void initilizeMap() {
        if (mGmap == null) {
            mGmap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            mGmap.getUiSettings().setZoomControlsEnabled(false);

            LatLng pos=new LatLng(currentLoc.getLatitude(),currentLoc.getLongitude());
            mapLoc= mGmap.addMarker(new MarkerOptions().position(pos)
        			.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_x)));
            if(cameraPos==null){
            	cameraPos=CameraPosition.builder().target(pos).zoom(17).build();
            	mGmap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
            }
            if(debugMode){
            	mGmap.addMarker(new MarkerOptions().position(pos).title("pt")
     					.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_x)));
				mGmap.addCircle(new CircleOptions().center(pos).radius(currentLoc.getAccuracy()).strokeWidth(2));
				
            }else{
            	mapLoc= mGmap.addMarker(new MarkerOptions().position(pos)
            			.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_x)));
            	mGmap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
            	line=mGmap.addPolyline(new PolylineOptions().add(pos));
            }
            if (overlayArray!=null){
            	loadLocs();
            }
            // check if map is created successfully or not
            if (mGmap == null) {
                Toast.makeText(getApplicationContext(),
                        "Sorry, unable to create maps", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }
	

	private void reset(){
		overlayArray.clear();
		currentLoc.reset();
		distance=0;
		if(line!=null){
			line.remove();
		}
	}
	private void loadLocs(){
		if (!overlayArray.isEmpty()){
			List<LatLng> ptList=new ArrayList<LatLng>();
			
			for(Location l:overlayArray){
				final LatLng pos = new LatLng(l.getLatitude(),l.getLongitude());
				 builder.include(pos);
				if(debugMode&&markerCount<=32){
					mGmap.addMarker(new MarkerOptions().position(pos).title("pt")
								.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_x)));
					mGmap.addCircle(new CircleOptions().center(pos).radius(l.getAccuracy()).strokeWidth(2));
				}else{
        				ptList.add(pos);
        			}
				}
			if (line==null){			
				line=mGmap.addPolyline(new PolylineOptions()
				.add(new LatLng(currentLoc.getLatitude(),currentLoc.getLongitude())));
			}
			if(!ptList.isEmpty()){
				line.setPoints(ptList);
			}
				}
			if (overlayArray.size()!=0){
				Location last=overlayArray.get(overlayArray.size()-1);
				mGmap.moveCamera(CameraUpdateFactory.newLatLng(
						new LatLng(last.getLatitude(),last.getLongitude())));
			}
		}
}



