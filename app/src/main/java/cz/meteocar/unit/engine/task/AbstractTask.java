package cz.meteocar.unit.engine.task;

import android.util.Log;

import java.util.TimerTask;

import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.event.AppEvent;
import cz.meteocar.unit.engine.event.ErrorViewType;
import cz.meteocar.unit.engine.event.NetworkErrorEvent;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.network.NetworkException;

/**
 * Created by Nell on 27.3.2016.
 */
public abstract class AbstractTask extends TimerTask {

    public abstract void runTask();

    @Override
    public void run() {
        Log.d(AppLog.LOG_TAG_DEFAULT, this.getClass().getName());
        try {
            runTask();
        } catch (Exception e) {
            Log.e(AppLog.LOG_TAG_DEFAULT, e.getMessage(), e.getCause());
        }
    }

    protected void postEvent(AppEvent event) {
        ServiceManager.getInstance().eventBus.post(event).asynchronously();
    }

    protected void postNetworkException(NetworkException e) {
        ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
    }

}
