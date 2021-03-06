package cz.meteocar.unit.engine.storage.helper;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.accel.event.AccelerationEvent;
import cz.meteocar.unit.engine.enums.RecordTypeEnum;
import cz.meteocar.unit.engine.event.AppEvent;
import cz.meteocar.unit.engine.event.EventType;
import cz.meteocar.unit.engine.gps.event.GPSPositionEvent;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.obd.event.OBDPidEvent;
import cz.meteocar.unit.engine.storage.DatabaseException;
import cz.meteocar.unit.engine.storage.MySQLiteConfig;
import cz.meteocar.unit.engine.storage.TripDetailVO;
import cz.meteocar.unit.engine.storage.helper.filter.AccelerationVO;
import cz.meteocar.unit.engine.storage.model.RecordEntity;

/**
 * Helper for saving and loading {@link RecordEntity}.
 */
public class RecordHelper extends AbstractHelper<RecordEntity> {

    private static final String TABLE_NAME = "record_details";
    private static final String COLUMN_NAME_TIME = "time";
    private static final String COLUMN_NAME_USER_ID = "user_id";
    private static final String COLUMN_NAME_TRIP_ID = "trip_id";
    private static final String COLUMN_NAME_TYPE = "type";
    private static final String COLUMN_NAME_JSON = "json";
    private static final String COLUMN_NAME_PROCESSED = "processed";

    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_ID + MySQLiteConfig.TYPE_ID + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_TIME + MySQLiteConfig.TYPE_INTEGER + " DEFAULT 0" + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_USER_ID + MySQLiteConfig.TYPE_TEXT + " DEFAULT ''" + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_TRIP_ID + MySQLiteConfig.TYPE_TEXT + " DEFAULT ''" + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_TYPE + MySQLiteConfig.TYPE_TEXT + " DEFAULT ''" + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_JSON + MySQLiteConfig.TYPE_TEXT + " DEFAULT ''" + MySQLiteConfig.COMMA_SEP +
                    COLUMN_NAME_PROCESSED + MySQLiteConfig.TYPE_BOOLEAN + " DEFAULT ''" +
                    " )";

    public static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final String SQL_GET_ALL = "SELECT  * FROM " + TABLE_NAME;

    public RecordHelper(DatabaseHelper helper) {
        super(helper, TABLE_NAME);
    }

    @Override
    public int save(RecordEntity obj) {
        try {
            ContentValues values = new ContentValues();

            values.put(COLUMN_NAME_TIME, obj.getTime());
            values.put(COLUMN_NAME_TYPE, obj.getType());
            values.put(COLUMN_NAME_USER_ID, obj.getUserName());
            values.put(COLUMN_NAME_TRIP_ID, obj.getTripId());
            values.put(COLUMN_NAME_JSON, obj.getJson());
            values.put(COLUMN_NAME_PROCESSED, obj.isProcessed());

            return this.innerSave(obj.getId(), values);
        } catch (DatabaseException exception) {
            Log.e(AppLog.LOG_TAG_DB, exception.getMessage(), exception);
            return -1;
        }
    }

    /**
     * Return List of entities based on trip Id and type of record.
     *
     * @param tripId    id of trip
     * @param type      type of record that we want.
     * @param processed if record was prepared for sending on server.
     * @return List of {@link RecordEntity}
     */
    public List<RecordEntity> getByTripIdAndType(String tripId, String type, boolean processed) {
        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor cursor = db.query(TABLE_NAME, null, COLUMN_NAME_TRIP_ID + " = ? and " + COLUMN_NAME_TYPE + " =  ? and " + COLUMN_NAME_PROCESSED + " = ?", new String[]{tripId, type, processed ? "1" : "0"}, null, null, null);

        return convertArray(cursor);
    }

    /**
     * Return number of processed records.
     *
     * @param processed If they are processed
     * @return number of records
     */
    public int getNumberOfRecord(Boolean processed) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COLUMN_NAME_PROCESSED + " = ?", new String[]{!processed ? "0" : "1"}, null, null, null);
        int count = cursor.getCount();
        cursor.close();

        return count;
    }

    /**
     * Sets records processed.
     *
     * @param id        List of id to process.
     * @param processed
     */
    public void updateProcessed(List<Integer> id, Boolean processed) {
        String[] array = new String[id.size()];


        for (int i = 0; i < id.size(); i++) {
            array[i] = String.valueOf(id.get(i));
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_PROCESSED, processed);

        SQLiteDatabase db = helper.getReadableDatabase();
        db.update(TABLE_NAME, values, "id IN (" + makePlaceholders(id.size()) + ")", array);
    }

    /**
     * Return list of Trips that have records stored in database/
     *
     * @return id of trips.
     */
    public List<String> getDistinctTrips(boolean processed) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<String> tripIds = new ArrayList<>();
        Cursor cursor = db.query(true, TABLE_NAME, new String[]{COLUMN_NAME_TRIP_ID}, COLUMN_NAME_PROCESSED + " = ?", new String[]{processed ? "1" : "0"}, COLUMN_NAME_TRIP_ID, null, null, null);

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                tripIds.add(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TRIP_ID)));
                cursor.moveToNext();
            }
        }
        cursor.close();
        return tripIds;
    }

    public List<String> getRecordsDistinctTypesForTrip(String tripId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<String> userIds = new ArrayList<>();
        Cursor cursor = db.query(true, TABLE_NAME, new String[]{COLUMN_NAME_TYPE}, COLUMN_NAME_TRIP_ID + " = ?", new String[]{tripId}, COLUMN_NAME_TYPE, null, null, null);

        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                userIds.add(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)));
                cursor.moveToNext();
            }
        }
        cursor.close();
        return userIds;
    }

    @Override
    protected RecordEntity convert(Cursor cursor) {
        RecordEntity obj = new RecordEntity();
        obj.setId(cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ID)));
        obj.setTime(cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIME)));
        obj.setTripId(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TRIP_ID)));
        obj.setUserName(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_USER_ID)));
        obj.setType(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)));
        obj.setJson(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_JSON)));
        obj.setProcessed(cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_PROCESSED)) != 0);
        return obj;
    }

    /**
     * Events to be stored.
     *
     * @param evt to be stored
     */
    public void save(AppEvent evt) {
        RecordEntity obj = new RecordEntity();

        obj.setTime(evt.getTimeCreated());
        obj.setUserName(evt.getUserId());
        obj.setTripId(evt.getTripId());
        obj.setProcessed(false);

        if (evt.getType() == EventType.EVENT_GPS_POSITION) {
            saveGPS(evt, obj);
        }

        if (evt.getType() == EventType.EVENT_OBD_PID) {
            saveOBD(evt, obj);
        }

        if (evt.getType() == EventType.EVENT_ACCEL) {
            saveACC(evt, obj);
        }

    }

    protected void saveACC(AppEvent evt, RecordEntity obj) {
        JSONObject jsonObj = new JSONObject();

        AccelerationEvent accelEvent = (AccelerationEvent) evt;

        obj.setType(RecordTypeEnum.TYPE_ACCEL.getValue());

        try {
            jsonObj.put(JsonTags.ACCELERATION_X, accelEvent.getX());
            jsonObj.put(JsonTags.ACCELERATION_Y, accelEvent.getY());
            jsonObj.put(JsonTags.ACCELERATION_Z, accelEvent.getZ());
        } catch (Exception e) {
            Log.d(AppLog.LOG_TAG_DB, "Exception while adding OBD event data to JSON object", e);
        }

        obj.setJson(jsonObj.toString());

        save(obj);
    }

    protected void saveGPS(AppEvent evt, RecordEntity obj) {
        JSONObject jsonObj = new JSONObject();

        obj.setType(RecordTypeEnum.TYPE_GPS.getValue());
        Location loc = ((GPSPositionEvent) evt).getLocation();
        if (loc == null) {
            return;
        }

        double m = 1000000.0;
        try {
            jsonObj.put(JsonTags.GPS_LAT, m * loc.getLatitude());
            jsonObj.put(JsonTags.GPS_LNG, m * loc.getLongitude());
            jsonObj.put(JsonTags.GPS_ALT, loc.getAltitude());
            jsonObj.put(JsonTags.GPS_ACC, loc.getAccuracy());
        } catch (Exception e) {
            Log.e(AppLog.LOG_TAG_DB, "Exception while adding GPS event data to JSON object", e);
        }

        obj.setJson(jsonObj.toString());

        ServiceManager.getInstance().getDB().incrementGpsDistance(loc);

        save(obj);
    }

    protected void saveOBD(AppEvent evt, RecordEntity obj) {
        OBDPidEvent obdEvent = (OBDPidEvent) evt;

        obj.setType(obdEvent.getMessage().getTag());

        JSONObject jsonObj = new JSONObject();

        try {
            jsonObj.put(JsonTags.OTHER_VALUE, ((OBDPidEvent) evt).getValue());
        } catch (Exception e) {
            Log.e(AppLog.LOG_TAG_DB, "Exception while adding OBD event data to JSON object", e);
        }

        obj.setJson(jsonObj.toString());

        if ("010D1".equals(obdEvent.getMessage().getCommand())) {
            ServiceManager.getInstance().getDB().incrementObdDistance(obdEvent);
        }

        save(obj);
    }

}
