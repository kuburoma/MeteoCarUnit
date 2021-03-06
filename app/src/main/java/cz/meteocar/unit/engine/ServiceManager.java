package cz.meteocar.unit.engine;

import android.content.Context;
import android.util.Log;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;

import cz.meteocar.unit.engine.accel.AccelerationService;
import cz.meteocar.unit.engine.clock.ClockService;
import cz.meteocar.unit.engine.event.AppEvent;
import cz.meteocar.unit.engine.gps.ServiceGPS;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.network.NetworkService;
import cz.meteocar.unit.engine.obd.OBDService;
import cz.meteocar.unit.engine.storage.DatabaseService;
import cz.meteocar.unit.engine.task.TaskManager;

/**
 * Service manager that manages all services in application.
 */
public class ServiceManager {

    public final String version = "1.15";

    private ClockService clock;
    private ServiceGPS gps;
    private OBDService obd;
    private DatabaseService db;
    private NetworkService network;
    private AccelerationService accel;
    private TaskManager taskManager;

    private Context context;

    // bus
    public MBassador<AppEvent> eventBus;

    private static final ServiceManager MY_SERVICE_MANAGER = new ServiceManager();

    public static ServiceManager getInstance() {
        return MY_SERVICE_MANAGER;
    }

    public Context getContext() {
        return context;
    }

    public ClockService getClock() {
        return clock;
    }

    public ServiceGPS getGPS() {
        return gps;
    }

    public OBDService getOBD() {
        return obd;
    }

    public DatabaseService getDB() {
        return db;
    }

    public NetworkService getNetwork() {
        return network;
    }

    public AccelerationService getAccel() {
        return accel;
    }

    /**
     * Inicializace manageru, start služeb
     */
    public void init(Context baseContext) {

        // context
        context = baseContext;

        // bus
        eventBus = new MBassador<>(BusConfiguration.SyncAsync());

        // vytvoření a spuštění služeb
        clock = new ClockService();
        gps = new ServiceGPS(context);
        obd = new OBDService(context);
        db = new DatabaseService(context);
        network = new NetworkService(context);
        accel = new AccelerationService(context);
        taskManager = new TaskManager();
        taskManager.initManager();
    }

    /**
     * Bezpečně ukončí služby
     */
    public void exitServices() {

        // nastavíme všem službám ukončovací flag
        clock.exit();
        gps.exit();
        obd.exit();
        db.exit();

        // teď jen voláme na jejich vláknech joiny
        try {
            clock.join(500);
        } catch (InterruptedException e) {
            Log.e(AppLog.LOG_TAG_DEFAULT, "Clock thread interrupted for calling join.", e);
        }
        try {
            obd.join(500);
        } catch (InterruptedException e) {
            Log.e(AppLog.LOG_TAG_DEFAULT, "OBD thread interrupted for calling join.", e);
        }
        try {
            db.join(500);
        } catch (InterruptedException e) {
            Log.e(AppLog.LOG_TAG_DEFAULT, "DB thread interrupted for calling join.", e);
        }

        // nastavíme na null, pro jistotu
        gps = null;
        obd = null;
        db = null;
        network = null;

        taskManager.stopAll();

    }
}
