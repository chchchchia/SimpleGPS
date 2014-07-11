package com.chchchChia.SimpleGPS;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;


import com.chchchChia.SimpleGPS.R;

import com.google.android.gms.location.LocationStatusCodes;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;


public class GPSservice extends Service implements LocationListener, GpsStatus.Listener {
    protected LocationManager locationManager;
    private static final double REJECT_TIME=3.0E4;//reject lastknown position if it's >30sec old, in millisec
    public static final String DATABASE_NAME = "GPS_SERV_DB";
    public static final String POINTS_TABLE_NAME = "LOCATION_POINTS";
    public static final String TRIPS_TABLE_NAME = "TRIPS";
    private static final String TAG="GPSservice";
    public static boolean SERVICE_RUNNING=false;
    public static Bundle update;
	private final DecimalFormat df = new DecimalFormat("####0.00");
	private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	protected static String distUnit="m";
	protected static String speedUnit="m/s";
	private int minUpdateDistance = 3;//in m
	private int minUpdateInt=2000;//in ms
	private int timeTaskLoopCount=0;
	private int timeTaskTimeout=10;
	private double minAccuracy=12;//in m
	private GpsStatus gpsStatus;
	private Context mContext;
	private Intent mIntent,tIntent;
	private Handler mHandler=new Handler();
	private NotificationManager mNoteMgr;
	private Notification note;
	private WakeLock wakeLock;
	private ArrayList<Location> overlayArray;
    private boolean isGPSEnabled = false;
    private boolean isNetworkEnabled = false;
    private boolean canGetLocation = false;
    private boolean chronoRunning=false;
    private boolean positionChanged=false;
    private boolean resetFlag=false;
    public static boolean onlyTimeMoving;
    public boolean LOGGING=false;
    protected static long startTime, stopTime, stopPeriod, timeRunning;
    private Location location, prevLoc;
    private long timeMoving=0;
    private double speed=0;//in m/s
    private double avgSpeed;
    private double avgSpeedQuick;
    private double distance=0;//in m
    private int rejectsSinceLastUpdate=0;
    
    private SQLiteDatabase sq;
    
    //TODO SQLITE-DBinit
    //TODO instantaneous avg speed
    //other things...
    
    
   public synchronized Bundle getUpdateBundle(){
	   return this.update;
   }
   

	public GPSservice(){
	}
	
	public void setChronoRunning(boolean b){
		chronoRunning=b;
	}
	public synchronized boolean save(){
		//TODO placeholder for saving overlayArray to SQL db
		return true;
	}
	
	public synchronized boolean load(){
		//TODO placeholder for loading overlayArray from SQL db
		return true;
	}
	
	public void reset(){
		this.distance=0;
		this.avgSpeed=0;
		this.avgSpeedQuick=0;
		this.startTime=0;
		this.stopTime=0;
		this.stopPeriod=0;
		this.timeRunning=0;
		this.resetFlag=true;
	}
	@Override
	public void onCreate(){
		//called on creation of the service
		overlayArray=new ArrayList<Location>();
		startTime=0;
        stopPeriod=0;
        timeRunning=0;
        stopTime=0;
		//read Preferences
		 PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(this);
	        speedUnit=prefs.getString("Speed units", "m/s");
	        distUnit=prefs.getString("Distance units", "m");
	        onlyTimeMoving=(prefs.getString("timePref", "TotTime").equals("MoveTime") ? true:false);
	}
	
	@Override
	public void onDestroy(){
		locationManager.removeUpdates(this);
		releaseWakeLock();
		stopForeground(true);
		SERVICE_RUNNING=false;
		stopSelf();
		}
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mIntent=new Intent("POSITION_UPDATE");
		tIntent=new Intent("TIME_UPDATE");

		mContext=getApplicationContext();
	    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	    wakeLock = pm.newWakeLock(pm.PARTIAL_WAKE_LOCK, "GPS_Serv");
	    if(!wakeLock.isHeld()){
	    	wakeLock.acquire();
	    }
	    getLocation();
	    updateBundle();
	    startTime=SystemClock.elapsedRealtime();
	    chronoRunning=true;
	    mHandler.removeCallbacks(mUpdateTimeTask);
	    mHandler.postDelayed(mUpdateTimeTask, 1000);
	    startForeground(2442, showNotification());
	    SERVICE_RUNNING=true;
	    return START_STICKY;
	}
	
	public static boolean isRunning(){
		return SERVICE_RUNNING;
	}
	
	public void getLocation(){
		try{
		locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
		this.isGPSEnabled=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		if (!isGPSEnabled) {
		//no gps provider is enabled
			Toast.makeText(mContext, "This device does not have GPS enabled.", Toast.LENGTH_SHORT).show();
		}else {
			this.canGetLocation = true;
            if (isGPSEnabled&&location==null&&locationManager!=null) {
                    locationManager.requestLocationUpdates(
                    		LocationManager.GPS_PROVIDER,                 		
                            minUpdateInt,
                            minUpdateDistance, this);
                    Location temp = locationManager
                            .getLastKnownLocation(LocationManager.GPS_PROVIDER); 
                if (temp != null&&
                		SystemClock.elapsedRealtime()-age_ms(temp)<REJECT_TIME&&temp.getAccuracy()<=minAccuracy) {
                        		overlayArray.add(temp);
                        		this.location=temp;
                        		this.prevLoc=location;
                        		speed=location.getSpeed();
	                            sendLocationBroadcast(mIntent);
                	}
              }         
		}
		}catch (Exception e) {
        e.printStackTrace();
        }
	}
	
	private Runnable mUpdateTimeTask =new Runnable(){
		
		@Override
		public void run() {	
		
			if(positionChanged){
				if(!chronoRunning){
					//If the position changed while the clock was paused, restart it
					chronoRunning=true;
				}
				positionChanged=false;
				timeTaskLoopCount=0;
			}else{
				timeTaskLoopCount++;
			}
			if(chronoRunning){//TODO does this really need to be checked every loop, or only on reset?
				if(stopTime!=0){
					GPSservice.stopPeriod+=SystemClock.elapsedRealtime()-GPSservice.stopTime;
					GPSservice.stopTime=0;
	 			}
				if(GPSservice.startTime==0){
					GPSservice.startTime=SystemClock.elapsedRealtime();
				}
				GPSservice.timeRunning=SystemClock.elapsedRealtime()-GPSservice.startTime-GPSservice.stopPeriod;
			}
			if (timeTaskLoopCount>=timeTaskTimeout){//if no loc updates received in 5 sec, set speed to 0 and stop clock
				if(chronoRunning&&onlyTimeMoving){
					GPSservice.stopTime=SystemClock.elapsedRealtime();
					chronoRunning=false;
				}
				speed=0;
				timeTaskLoopCount=0;
			}
			sendTimeBroadcast(tIntent);
			mHandler.postDelayed(mUpdateTimeTask, 1000);
			
		}
    };
	private synchronized void updateBundle(){
		if (update==null){
			update= new Bundle();
		}
		update.clear();
		update.putParcelableArrayList("overlayArray", overlayArray);
		update.putParcelable("location", this.location);
		update.putDouble("distance", distance);
		update.putDouble("avgSpeed", avgSpeed);
	}
	
    
	private void sendLocationBroadcast(Intent intent){
	    intent.putExtra("location", location);
		intent.putExtra("distance", distance);
		intent.putExtra("avgSpeed", avgSpeed);
	    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void sendTimeBroadcast(Intent intent){
		intent.putExtra("timeRunning", timeRunning);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	//TODO add time here
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private Notification showNotification(){
		mNoteMgr=(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		 Intent notificationIntent = new Intent(getApplicationContext(), MainOSD.class);
		 PendingIntent pintent=PendingIntent.getActivity(mContext, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		 String text="SimpleGPS"+" is running.";
		 String statusStr=unitConverter_Distance(this.distance)+" @ "+ unitConverter_Speed(this.location!=null?this.location.getSpeed():0);
		 Notification not = new Notification.Builder(mContext)
		 	.setContentTitle(text)
		 	.setContentText(statusStr)
		 	.setSmallIcon(R.drawable.icon)
		 	.setOngoing(true)
		 	.setContentIntent(pintent)
		 	.build();
		 return not;
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

	
	@Override
	public void onLocationChanged(Location location) {
	//	Log.d("Location Update","via"+location.getProvider());
	//	Log.d("accuracy is", Float.toString(location.getAccuracy()));
	//	Log.d("Location Update","distance is "+distance);
		if(this.location==null){
			if(location.getAccuracy()<=minAccuracy){
				this.location=location;
				if(this.prevLoc==null){
					this.prevLoc=location;
				}
				sendLocationBroadcast(mIntent);
			}else{
				if(this.prevLoc==null){
					this.prevLoc=location;
				}
				return;
			}
		}

		if(age_ms(location)-age_ms(this.location)>REJECT_TIME){
			this.location=location;
			return;
		}
		if(isValidUpdate(location)){//if update is valid, accept it and update values
			
			if (!location.hasAltitude()&&this.location!=null){
				//if the location does not have an altitude, give it the last altitude
				//TODO update this later to include taking an average on the surrounding altitudes and assigning that as the altitude
				location.setAltitude(this.location.getAltitude());
			}
			location.setSpeed(Math.abs(speedCheck(location)));			

			
			distance+=location.distanceTo(this.location);
			Log.d(TAG,"Distance update-"+Double.toString(distance));
			overlayArray.add(location);

			prevLoc=this.location ;
			this.location = location;
			timeMoving=SystemClock.elapsedRealtime()-startTime;
			avgSpeed();
			if(LOGGING){
				//TODO implement this
				//logLoc(location);
			}
			updateBundle();
			positionChanged=true;
			sendLocationBroadcast(mIntent);
			mNoteMgr.notify(2442, showNotification());
		}
	}
	
	//This method determines if the current update is an error or representative of an actual movement
	private boolean isValidUpdate(Location loc){
		double deltaVel=2;
		int rejectLimit=3;
		
		if (!bearingCheck(loc)){
			if (rejectsSinceLastUpdate<rejectLimit){
				rejectsSinceLastUpdate++;
				Log.d(TAG,"Turn down for what?"+"bearing");
				return false;
			}
		}
		if (Math.abs(loc.getSpeed()-prevLoc.getSpeed())>deltaVel){
			if (rejectsSinceLastUpdate<rejectLimit){
				rejectsSinceLastUpdate++;
				Log.d(TAG,"Turn down for what?"+"speed");
				return false;
			}
		}
		if (loc.getAccuracy()>minAccuracy){
			Log.d(TAG,"Turn down for what?"+"High Error");
			return false;
		}
		if (loc.distanceTo(location)<loc.getAccuracy()){
			//TODO accept the point if accuracy is > last point, but do NOT add distance
			if(loc.getAccuracy()<location.getAccuracy()){
				Log.d(TAG,"accepted for better accuracy");
				if(!loc.hasAltitude()){
				loc.setAltitude(this.location.getAltitude());
				}
				//loc.setSpeed(Math.abs(speedCheck(loc)));
				//SHould speed be set to zero? ie
				//loc.setSpeed(0);
				if(overlayArray.size()>0){
				overlayArray.remove(overlayArray.size()-1);
				}
				overlayArray.add(loc);
				
				this.location=loc;
				
			}
			Log.d(TAG,"Turn down for what?"+"in Cone of uncertanity");
			return false;
		}
		rejectsSinceLastUpdate=0;
		return true;
	}
	//TODO put this on GUI
	private double avgSpeed(){
		if(timeMoving!=0){
			avgSpeed=this.distance/(this.timeMoving/1000);
		}else{
			avgSpeed=0;
		}
		return avgSpeed;
	}
	private boolean bearingCheck(Location l){
		//returns true if the bearing of the new point is not too out of wack compared to the last bearing
		float bearingDiff;
		if(l.hasBearing()){//if l has a bearing, cool, go with it
			bearingDiff=getBearingDiff(l ,this.location);
		}else{
			//if not, then compare the bearings of prevprevLoc and prevLoc to prevLoc and current loc, respectively
			if(!this.location.hasBearing()){
				this.location.setBearing(this.location.bearingTo(l));
			}
			bearingDiff=getBearingDiff(prevLoc,location);
			if(bearingDiff>15){
				this.location.removeBearing();
			}
		}
		return bearingDiff<=15 ? true:false;
		
	}
	private float getBearingDiff(Location currLoc, Location prevLoc){
		if(!currLoc.hasBearing()){
			currLoc.setBearing(prevLoc.bearingTo(currLoc));
		}
		float bear1=currLoc.getBearing();
		float bear2=prevLoc.getBearing();
		return bear1-bear2>180 ? (360-bear1)+bear2:Math.abs(bear1-bear2);
	}
	
	private float speedCheck(Location l){
		//update this to account for altitude if loc has it
		if (l.hasSpeed()&&l.getAccuracy()<=5){
			return l.getSpeed();
		}else{
			float dist=l.distanceTo(prevLoc);
			float time = age_ms(l)-age_ms(prevLoc);
			l.removeSpeed();
			return time!=0 ? dist/time : 0;
		}
	}
	public long age_ms(Location last) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            return age_ms_api_17(last);
        return age_ms_api_pre_17(last);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private long age_ms_api_17(Location last) {
        return (SystemClock.elapsedRealtimeNanos() - last
                .getElapsedRealtimeNanos()) / 1000000;
    }

    private long age_ms_api_pre_17(Location last) {
        return System.currentTimeMillis() - last.getTime();
    }
    
    private void logLoc(Location loc){
    	try {
            if (loc.hasAccuracy() && loc.getAccuracy() <= minAccuracy) {
                    //pointIsRecorded = true;
                    GregorianCalendar greg = new GregorianCalendar();
                    TimeZone tz = greg.getTimeZone();
                    int offset = tz.getOffset(System.currentTimeMillis());
                    greg.add(Calendar.SECOND, (offset/1000) * -1);
                    StringBuffer queryBuf = new StringBuffer();
                    sq.execSQL("CREATE TABLE IF NOT EXISTS "+POINTS_TABLE_NAME+ " (GMTTIMESTAMP VARCHAR, LATITUDE REAL, LONGITUDE REAL," +
                            "ALTITUDE REAL, ACCURACY REAL, SPEED REAL, BEARING REAL);");
                    queryBuf.append("INSERT INTO "+POINTS_TABLE_NAME+
                                    " (GMTTIMESTAMP,LATITUDE,LONGITUDE,ALTITUDE,ACCURACY,SPEED,BEARING) VALUES (" +
                                    "'"+timestampFormat.format(greg.getTime())+"',"+
                                    loc.getLatitude()+","+
                                    loc.getLongitude()+","+
                                    (loc.hasAltitude() ? loc.getAltitude() : "NULL")+","+
                                    (loc.hasAccuracy() ? loc.getAccuracy() : "NULL")+","+
                                    (loc.hasSpeed() ? loc.getSpeed() : "NULL")+","+
                                    (loc.hasBearing() ? loc.getBearing() : "NULL")+");");
                    Log.i("GPS_SERV_LOG", queryBuf.toString());
                    sq = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READWRITE, null);
                    sq.execSQL(queryBuf.toString());
            } 
    } catch (Exception e) {
            Log.e("GPS_SERV_LOG", e.toString());
    } finally {
            if (sq!=null&&sq.isOpen())
                    sq.close();
    }
    }
    
    
	private void releaseWakeLock() {
	    if (wakeLock != null && wakeLock.isHeld()) {
	      wakeLock.release();
	      wakeLock = null;
	    }
	  }
	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		if(status==LocationProvider.AVAILABLE){
			canGetLocation=true;
		}else if(status==LocationProvider.OUT_OF_SERVICE||status==LocationProvider.TEMPORARILY_UNAVAILABLE){
			canGetLocation=false;
		}
	}


	
	@Override
	public void onProviderEnabled(String provider) {
		if(provider.equals(LocationManager.GPS_PROVIDER)){
		canGetLocation=true;
		getLocation();
		}
		
	}
	 public class LocalBinder extends Binder {
	        GPSservice getService() {
	            return GPSservice.this;
	        }
	    }
	@Override
	public void onProviderDisabled(String provider) {
		if(provider.equals(LocationManager.GPS_PROVIDER)){
		this.canGetLocation=false;
		}
		Toast.makeText(this.mContext, provider.toString()+" is not enabled!", Toast.LENGTH_SHORT).show();;	
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	private final IBinder mBinder = new LocalBinder();

	
	@Override
	public void onGpsStatusChanged(int event) {
		/*gpsStatus=locationManager.getGpsStatus(gpsStatus);
		switch (event) {
		//TODO implement this, including code to account for loss in gps signal (incld alert)
        case GpsStatus.GPS_EVENT_STARTED:
        	Log.d("Event",gpsStatus.toString());
            break;

        case GpsStatus.GPS_EVENT_STOPPED:
        	Log.d("EventStop",gpsStatus.toString());
            break;

        case GpsStatus.GPS_EVENT_FIRST_FIX:
        	Log.d("FirstFix",gpsStatus.toString());
            break;

        case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
            Log.d("Sats",gpsStatus.toString());
            break;
    }*/
		
	}

}
