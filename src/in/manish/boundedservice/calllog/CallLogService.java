package in.manish.boundedservice.calllog;
import in.manish.boundedservice.R;
import in.manish.boundedservice.util.AppLog;
import in.manish.boundedservice.util.WidgetUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;


public class CallLogService extends Service 
{
	private static boolean isRunning = false;
	private static final String COMMA = "," ;
	private static final int NOTIFICATION_ID = 1;

	public static final int MSG_REGISTER_CLIENT = 1;
	public static final int MSG_UNREGISTER_CLIENT = 2;
	public static final int MSG_SET_INT_VALUE = 3;
	public static final int MSG_SET_STRING_VALUE = 4;
	public static final int MSG_START_RECORDING= 5;
	public static final int MSG_STOP_RECORDING= 6;

	private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
	private int mValue = 0; // Holds last value set by a client.

	private StringBuilder pointBufferFiller = new StringBuilder();// seperated by comma
	private Date enddate;
	private Date startdate;
	private DecimalFormat df3 = new DecimalFormat("#0.0000");
	private DecimalFormat df6 = new DecimalFormat("#0.000000");
	private DecimalFormat df0 = new DecimalFormat("#0");

	private boolean isStartFirstTimeStamp = false;
	private boolean isRecording;
	private boolean isLastCall;

	/*if frequency 2 and time duration 3 minute(180) so countmethodcall will be 360*/
	private PendingIntent contentIntent;
	private NotificationManager mNotificationManager;
	private final Messenger mMessenger = new Messenger(new IncomingMessageHandler()); // Target we publish for clients to send messages to IncomingHandler.
	private File dir;
	private File fileLog;
	private FileWriter writer;
	public static final String upLoadServerUri = "http://121.240.116.173/accelerometer/file.php";

	public static boolean isRunning() {
		return isRunning;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		AppLog.d("Service Started.");
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		startForeground();
		
		isRunning = true;
		this.dir = new File(Environment.getExternalStorageDirectory() + "/AccelLogger");
		this.dir.mkdirs();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		AppLog.d("Received start id " + startId + ": " + intent);
		return START_STICKY; // Run until explicitly stopped.
	}

	@Override
	public IBinder onBind(Intent intent) {
		AppLog.d("onBind");
		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mNotificationManager.cancel(R.string.service_started); // Cancel the persistent notification.
		stopForeground(true);
		Log.i("MyService", "Service Stopped.");
		isRunning = false;
	}

	/**
	 * Handle incoming messages from MainActivity
	 */
	private class IncomingMessageHandler extends Handler { // Handler of incoming messages from clients.
		@Override
		public void handleMessage(Message msg) {
			AppLog.d("handleMessage: " + msg.what);
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				//				stopPreRecordingData();
				break;
			case MSG_SET_INT_VALUE:
				//				incrementBy = msg.arg1;
				break;
			case MSG_START_RECORDING:
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						startFetchingCallLog();
						
					}
				}).start();
				
				break;
			case MSG_STOP_RECORDING:
				stopPreRecordingData();
				break;

			default:
				super.handleMessage(msg);
			}
		}
	}

	private void startForeground() {
		startForeground(1, getMyActivityNotification(""));
	}

	private Notification getMyActivityNotification(String text){
		// The PendingIntent to launch our activity if the user selects
		// this notification
		CharSequence title = "Accelerometer Data";//getText(R.string.title_activity);
		if(contentIntent == null) {
			contentIntent = PendingIntent.getActivity(this,
					0, new Intent(this, MainActivity.class), 0);
		}
		Notification notification = new NotificationCompat.Builder(this)
		.setContentTitle(title)
		.setContentText(text)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentIntent(contentIntent).build();  
		return notification;    
	}
	/**
this is the method that can be called to update the Notification
	 */
	private void updateNotification(String text) {
		Notification notification = getMyActivityNotification(text);
		mNotificationManager.notify(NOTIFICATION_ID, notification);
	}

	private void resetSensorDataFiller()
	{
		pointBufferFiller.setLength(0);// more faster than insert and allocate new one
	}

	/**
	 * Send the data to all clients.
	 * @param intvaluetosend The value to send.
	 */
	private void sendMessageToUI(String countRows, int intvaluetosend, boolean enableStartButton) {
		Iterator<Messenger> messengerIterator = mClients.iterator();		
		while(messengerIterator.hasNext()) {
			Messenger messenger = messengerIterator.next();
			try {
				// Send data as an Integer				
				messenger.send(Message.obtain(null, MSG_SET_INT_VALUE, intvaluetosend, 0));

				// Send data as a String
				Bundle bundle = new Bundle();
				//				bundle.putString("str1", "x: " + intvaluetosend + "cd");
				bundle.putString("str1", countRows);
//				bundle.putFloat("x", sensorX);
//				bundle.putFloat("y", sensorY);
//				bundle.putFloat("z", sensorZ);
				bundle.putBoolean("startButtonState", enableStartButton);
				bundle.putFloat("duration", intvaluetosend);
				Message msg = Message.obtain(null, MSG_SET_STRING_VALUE);
				msg.setData(bundle);
				messenger.send(msg);

			} catch (RemoteException e) {
				// The client is dead. Remove it from the list.
				mClients.remove(messenger);
			}
		}
	}

	private void startFetchingCallLog() {
		Cursor curLog = CallLogHelper.getAllCallLogs(getContentResolver());
		int countRows = 0 ;
		AppLog.e("before while");
		while (curLog.moveToNext()) {
			
			countRows++;
//			= curLog.getString(curLog
//					.getColumnIndex(android.provider.CallLog.Calls._ID));
			sendMessageToUI(countRows+"", 0, false);
			AppLog.e("after sendmsgui");
			updateNotification("total call logs: " + countRows);
			String callNumber = curLog.getString(curLog
					.getColumnIndex(android.provider.CallLog.Calls.NUMBER));
			
//			conNumbers.add(callNumber);

			String callName = curLog
					.getString(curLog
							.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME));
			if (callName == null) {
//				conNames.add("Unknown");
			} else {
//				conNames.add(callName);
			}
			String callDate = curLog.getString(curLog
					.getColumnIndex(android.provider.CallLog.Calls.DATE));
			SimpleDateFormat formatter = new SimpleDateFormat(
					"dd-MMM-yyyy HH:mm");
			String dateString = formatter.format(new Date(Long
					.parseLong(callDate)));
//			conDate.add(dateString);

			String callType = curLog.getString(curLog
					.getColumnIndex(android.provider.CallLog.Calls.TYPE));
			if (callType.equals("1")) {
//				conType.add("Incoming");
			} else {
//				conType.add("Outgoing");
			}
			String duration = curLog.getString(curLog
					.getColumnIndex(android.provider.CallLog.Calls.DURATION));
//			conTime.add(duration);
			AppLog.e("Call: "+countRows+ " : "+callNumber +" : " +callName);

		}

		sendMessageToUI("total calls : "+ countRows+"", 1, true);
		stopForeground(true);
		stopSelf();
	}
	private void startRecordingData() {
		if (! isRecording)
		{
			resetSensorDataFiller();
			isStartFirstTimeStamp = true;
		}
		// crate a new file every time user press start button.

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
			AppLog.showToast(this, "Memory card is unavailable or it is mounted to computer.");
			//handle case of no SDCARD present
		} else {
			File file = new File(Environment.getExternalStorageDirectory()
					+File.separator
					+"Accelerometer");//folder name
			file.mkdirs();
			String fileName = "_"+Calendar.getInstance().getTimeInMillis()+".txt";
			AppLog.e(fileName);
			fileLog = new File(file, fileName);
			try {
				fileLog.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		try
		{
			this.writer = new FileWriter(this.fileLog);
		} catch (Exception e) {
			e.printStackTrace();
		}

		isRecording = true;
		startdate = Calendar.getInstance().getTime();
		enddate = Calendar.getInstance().getTime();
		//		duration = "0";
	}

	private void stopPreRecordingData() {
		if(isRecording ==false) {
			return;
		}
		enddate = Calendar.getInstance().getTime();
		playSound();
		//		showEditDialog();
		isRecording = false;
		isStartFirstTimeStamp = false;
		/********************* file computation and uploading file to server*************/
		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String filePath = fileLog.getPath();
		updateNotification("uploading file ....");
		sendMessageToUI("done", 1, true);

		if(! WidgetUtil.checkInternetConnection(this)) {
			updateNotification("No Internet Connection.");
			return;
		}
		/***********************************************************************************/
		stopForeground(true);
		stopSelf();

	}

	private void playSound() {
		try {
			Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			if(alert == null){
				// alert is null, using backup
				alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);	
				if(alert == null){  // I can't see this ever being null (as always have a default notification) but just incase
					// alert backup is null, using 2nd backup
					alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);               
				}
			}
			Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alert);
			r.play();
		} catch (Exception e) {
			AppLog.e(e.getMessage());
		}
	}
}