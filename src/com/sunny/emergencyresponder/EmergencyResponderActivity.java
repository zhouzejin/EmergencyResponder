package com.sunny.emergencyresponder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;

public class EmergencyResponderActivity extends Activity {
	
	public static final String SMS_RECEIVED = 
			"android.provider.Telephony.SMS_RECEIVED";
	public static final String SENT_SMS = 
			"com.sunny.emergencyresponder.SEND_SMS";
	
	ReentrantLock lock;
	
	CheckBox locationCheckBox;
	
	ArrayList<String> requesters;
	ArrayAdapter<String> aa;
	
	BroadcastReceiver emergencyResponseRequestReceiver = new BroadcastReceiver() {
		
		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SMS_RECEIVED)) {
				String queryString = getString(R.string.querystring).toLowerCase();

				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					Object[] pdus = (Object[]) bundle.get("pdus");
					SmsMessage[] messages = new SmsMessage[pdus.length];
					for (int i = 0; i < pdus.length; i++) {
						messages[i] = SmsMessage
								.createFromPdu((byte[]) pdus[i]);
					}

					for (SmsMessage message : messages) {
						if (message.getMessageBody().toLowerCase()
								.contains(queryString)) 
							requestReceived(message.getOriginatingAddress());
					}
				}
			}
		}
	};
	
	private BroadcastReceiver attemptedDeliveryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(SENT_SMS)) {
				if (getResultCode() != Activity.RESULT_OK) {
					String recipient = intent.getStringExtra("recipient");
					requestReceived(recipient);
				}
			}
		}
		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_emergency_responder);
		
		lock = new ReentrantLock();
		requesters = new ArrayList<String>();
		wireUpControls();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		IntentFilter filter = new IntentFilter(SMS_RECEIVED);
		registerReceiver(emergencyResponseRequestReceiver, filter);
		
		IntentFilter attemptedDeliveryFilter = new IntentFilter(SENT_SMS);
		registerReceiver(attemptedDeliveryReceiver, attemptedDeliveryFilter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		unregisterReceiver(emergencyResponseRequestReceiver);
		unregisterReceiver(attemptedDeliveryReceiver);
	}

	protected void requestReceived(String from) {
		if (!requesters.contains(from)) {
			lock.lock();
			requesters.add(from);
			aa.notifyDataSetChanged();
			lock.unlock();
		}
	}

	private void wireUpControls() {
		locationCheckBox = (CheckBox) findViewById(R.id.checkboxSendLocation);
		ListView myListView = (ListView) findViewById(R.id.myListView);
		
		int layoutID = android.R.layout.simple_list_item_1;
		aa = new ArrayAdapter<>(this, layoutID, requesters);
		myListView.setAdapter(aa);
		
		Button okButton = (Button) findViewById(R.id.okButton);
		okButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				respond(true, locationCheckBox.isChecked());
			}
		});
		
		Button notOkButton = (Button) findViewById(R.id.notOkButton);
		notOkButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				respond(false, locationCheckBox.isChecked());
			}
		});
		
		Button autoResponderButton = (Button) findViewById(R.id.autoResponder);
		autoResponderButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				startAutoResponder();
			}
		});
	}

	protected void startAutoResponder() {
		// TODO Auto-generated method stub
		
	}

	protected void respond(boolean ok, boolean includeLocation) {
		String okString = getString(R.string.allClearText);
		String notOkString = getString(R.string.maydayText);
		String outString = ok ? okString : notOkString;
		
		@SuppressWarnings("unchecked")
		ArrayList<String> requestersCopy = 
				(ArrayList<String>) requesters.clone();
		
		for (String to : requestersCopy) {
			respond(to, outString, includeLocation);
		}
	}

	private void respond(String to, String response, boolean includeLocation) {
		// 从需要响应的接收人列表中删除目标
		lock.lock();
		requesters.remove(to);
		aa.notifyDataSetChanged();
		lock.unlock();
		
		SmsManager sms = SmsManager.getDefault();
		
		// 发送消息
		sms.sendTextMessage(to, null, response, null, null);
		
		StringBuilder sb = new StringBuilder();
		
		// 找到当前位置，如果有必要，将它作为SMS消息发送出去
		if (includeLocation) {
			String ls = Context.LOCATION_SERVICE;
			LocationManager lm = (LocationManager) getSystemService(ls);
			Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			
			if (l == null) {
				sb.append("Location unknown.");
			} else {
				sb.append("I'm @:\n");
				sb.append(l.toString() + "\n");
				
				List<Address> addresses;
				Geocoder g = new Geocoder(getApplicationContext(), Locale.getDefault());
				
				try {
					addresses = g.getFromLocation(l.getLatitude(), l.getLongitude(), 1);
					if (addresses != null) {
						Address currentAddress = addresses.get(0);
						if (currentAddress.getMaxAddressLineIndex() > 0) {
							for (int i = 0; i < currentAddress.getMaxAddressLineIndex(); i++) {
								sb.append(currentAddress.getAddressLine(i));
								sb.append("\n");
							}
						} else {
							if (currentAddress.getPostalCode() != null) {
								sb.append(currentAddress.getPostalCode());
							}
						}
					}
				} catch (IOException e) {
					Log.e("SMS_RESPONDER", "IO Exception.", e);
				}
				
				ArrayList<String> locationMsgs = sms.divideMessage(sb.toString());
				for (String locationMsg : locationMsgs) {
					Intent intent = new Intent(SENT_SMS);
					intent.putExtra("recipient", to);
					PendingIntent sentPI = PendingIntent
							.getBroadcast(getApplicationContext(), 0, intent, 0);
					
					sms.sendTextMessage(to, null, locationMsg, sentPI, null);
				}
			}
		}
	}

}
