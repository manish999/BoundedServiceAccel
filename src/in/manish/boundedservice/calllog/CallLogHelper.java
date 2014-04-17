package in.manish.boundedservice.calllog;

import in.manish.boundedservice.util.AppLog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;

public class CallLogHelper {

	public static Cursor getAllCallLogs(ContentResolver cr) {
		// reading all data in descending order according to DATE
		String strOrder = android.provider.CallLog.Calls.DATE + " DESC";
		Uri callUri = android.provider.CallLog.Calls.CONTENT_URI;///Uri.parse("content://call_log/calls");
		AppLog.e("Before query");
		Cursor curCallLogs = cr.query(callUri, /*uri*/
				null, /*projection*/
				null,/*selection*/
				null,/*selection arguments*/
				strOrder/*sort by*/);
		AppLog.e("After query");
		return curCallLogs;
	}

	public static void insertPlaceholderCall(ContentResolver contentResolver,
			String name, String number) {
		ContentValues values = new ContentValues();
		values.put(CallLog.Calls.NUMBER, number);
		values.put(CallLog.Calls.DATE, System.currentTimeMillis());
		values.put(CallLog.Calls.DURATION, 0);
		values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
		values.put(CallLog.Calls.NEW, 1);
		values.put(CallLog.Calls.CACHED_NAME, name);
		values.put(CallLog.Calls.CACHED_NUMBER_TYPE, 0);
		values.put(CallLog.Calls.CACHED_NUMBER_LABEL, "");
		Log.d("Call Log", "Inserting call log placeholder for " + number);
		contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
	}

}
