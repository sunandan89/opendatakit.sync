package org.opendatakit.sync.service;

import java.util.Arrays;

import org.opendatakit.sync.SyncPreferences;
import org.opendatakit.sync.SyncProcessor;
import org.opendatakit.sync.SynchronizationResult;
import org.opendatakit.sync.Synchronizer;
import org.opendatakit.sync.TableFileUtils;
import org.opendatakit.sync.TableResult;
import org.opendatakit.sync.aggregate.AggregateSynchronizer;
import org.opendatakit.sync.exceptions.InvalidAuthTokenException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.IBinder;
import android.util.Log;

public class OdkSyncService extends Service {

	private static final String LOGTAG = "OdkSyncService";

	public static final String BENCHMARK_SERVICE_PACKAGE = "org.opendatakit.sync";

	public static final String BENCHMARK_SERVICE_CLASS = "org.opendatakit.sync.service.OdkSyncService";

	private SyncStatus status;

	private String appName;
	private SyncThread syncThread;
	private OdkSyncServiceInterfaceImpl serviceInterface;

	@Override
	public void onCreate() {
		serviceInterface = new OdkSyncServiceInterfaceImpl(this);
		status = SyncStatus.INIT;

		if (appName == null) {
			appName = TableFileUtils.getDefaultAppName();
		}
		
		syncThread = new SyncThread(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// actions if received a start command
		Log.i(LOGTAG, "Service is starting");

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return serviceInterface;
	}

	@Override
	public void onDestroy() {
		Log.i(LOGTAG, "Service is shutting down");

	}

	public void sync() {
		if(!syncThread.isAlive() || syncThread.isInterrupted()) {
			syncThread.setPush(false);
			status = SyncStatus.SYNCING;
			syncThread.run();
		}
	}

	public void push() {
		if(!syncThread.isAlive() || syncThread.isInterrupted()) {
			syncThread.setPush(true);
			status = SyncStatus.SYNCING;
			syncThread.run();
		}
		
	}
	
	public SyncStatus getStatus() {
		return status;
	}

	// TEMPORARY THREAD while transition API
	private class SyncThread extends Thread {
		
		private Context cntxt;
		
		private boolean push;
		
		public SyncThread(Context context) {
			this.cntxt = context;
			this.push = false;
		}
		
		public void setPush(boolean push) {
			this.push = push;
		}
		
		@Override
		public void run() {
			try {

					SyncPreferences prefs = new SyncPreferences(cntxt, appName);
				Synchronizer synchronizer = new AggregateSynchronizer(appName,
						prefs.getServerUri(), prefs.getAuthToken());
				SyncProcessor processor = new SyncProcessor(cntxt, appName,
						synchronizer, new SyncResult());

				SynchronizationResult results = processor.synchronize(push,
						push, true);

				// default to sync complete
				status = SyncStatus.SYNC_COMPLETE;
				for (TableResult result : results.getTableResults()) {
					TableResult.Status status = result.getStatus();

				}

				Log.e(LOGTAG, "[SyncNowTask#doInBackground] timestamp: "
						+ System.currentTimeMillis());
			} catch (InvalidAuthTokenException e) {
				status = SyncStatus.AUTH_RESOLUTION;
			} catch (Exception e) {
				Log.e(LOGTAG,
						"[exception during synchronization. stack trace:\n"
								+ Arrays.toString(e.getStackTrace()));
				status = SyncStatus.NETWORK_ERROR;
			}
		}

	}

	
}
