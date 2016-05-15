package cz.meteocar.unit.engine.gps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import net.engio.mbassy.bus.MBassador;

import java.util.concurrent.atomic.AtomicBoolean;

import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.event.AppEvent;
import cz.meteocar.unit.engine.gps.event.GPSPositionEvent;
import cz.meteocar.unit.engine.gps.event.GPSStatusEvent;
import cz.meteocar.unit.engine.log.AppLog;

/**
 *GPS service listener
 */
public class ServiceGPS extends Thread implements LocationListener, GpsStatus.Listener {

    public static final int STATUS_NO_HARDWARE = 0;
    public static final int STATUS_GPS_OFFLINE = 1;
    public static final int STATUS_NO_FIX = 2;
    public static final int STATUS_FIXED = 3;
    private int status;

    private LocationManager locationManager;
    private Criteria criteria;
    private Location latestLocation;

    private AtomicBoolean locationUpdated;
    private boolean threadRun;
    private boolean threadFinalized = false;

    private Context context;
    private MBassador<AppEvent> eventBus;


    public ServiceGPS(Context ctx) {
        context = ctx;

        // bool
        locationUpdated = new AtomicBoolean(false);

        // event bus
        eventBus = ServiceManager.getInstance().eventBus;

        //def. status
        status = STATUS_GPS_OFFLINE;
    }


    /**
     * Starts GPS service.
     */
    @Override
    public synchronized void start() {
        Log.d(AppLog.LOG_TAG_GPS, "GPS start()");
        threadRun = true;
        super.start();
    }

    /**
     * Is service running
     */
    public boolean isRunning() {
        return threadRun;
    }

    /**
     * Was service ended.
     */
    public boolean isFinalized() {
        return threadFinalized;
    }

    /**
     * Exits threads safely.
     */
    public void exit() {
        Log.d(AppLog.LOG_TAG_GPS, "GPS Exit reuqired");
        threadRun = false;
    }

    @Override
    public void run() {

        init();

        while (threadRun) {
            try {
                this.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(AppLog.LOG_TAG_NETWORK, "ServiceGPS.sleep() caused error.", e);
            }

            eventBus.post(new GPSPositionEvent(getLocation())).asynchronously();
        }

        Log.d(AppLog.LOG_TAG_GPS, "GPS E");
        threadFinalized = true;
    }

    /**
     * Inicializace GPS proměnných, nastavení listeneru polohy
     * <p/>
     * Initialization of GPS environment, setting up listeners.
     */
    private void init() {

        // Look if we have even hardware in device.
        boolean presentGPS = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        if (!presentGPS) {
            status = STATUS_NO_HARDWARE;
        }

        // Sets localization service.
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        final ServiceGPS thisObject = this;
        new Handler(context.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1, 2, thisObject);
                locationManager.addGpsStatusListener(thisObject);
            }
        });
    }

    private synchronized void setLocation(Location location) {
        latestLocation = location;
        locationUpdated.set(true);
    }

    public Location getLocation() {
        return latestLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        setLocation(location);
    }

    @Override
    public void onStatusChanged(String s, int state, Bundle bundle) {
        Log.d(AppLog.LOG_TAG_GPS, "GPS Provider status: " + state);
        if (state == LocationProvider.OUT_OF_SERVICE) {
            status = STATUS_GPS_OFFLINE;
        }
        if (state == LocationProvider.OUT_OF_SERVICE) {
            status = STATUS_GPS_OFFLINE;
        }
        updateStatus();
    }

    @Override
    public void onProviderEnabled(String s) {

        Log.d(null, "GPS provider enabled");

        status = STATUS_NO_FIX;
        updateStatus();
    }

    @Override
    public void onProviderDisabled(String s) {

        Log.d(null, "GPS provider disabled");
        status = STATUS_GPS_OFFLINE;
        updateStatus();
    }

    @Override
    public void onGpsStatusChanged(int state) {

        if (state == GpsStatus.GPS_EVENT_STARTED) {
            status = STATUS_NO_FIX;
        }

        if (state == GpsStatus.GPS_EVENT_STOPPED) {
            status = STATUS_GPS_OFFLINE;
        }

        if (state == GpsStatus.GPS_EVENT_FIRST_FIX) {
            status = STATUS_FIXED;
        }

        updateStatus();
    }

    private void updateStatus() {
        eventBus.post(new GPSStatusEvent(status)).asynchronously();
    }

}
