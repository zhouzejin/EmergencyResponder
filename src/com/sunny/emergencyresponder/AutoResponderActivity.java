package com.sunny.emergencyresponder;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

public class AutoResponderActivity extends Activity {

	public static final String autoResponsePref = "autoResponsePref";
	public static final String responseTextPref = "responseTextPref";
	public static final String includeLocPref = "includeLocPref";
	public static final String respondForPref = "respondForPref";
	public static final String defaultResponseText = "All clear";
	public static final String alarmAction = 
			"com.sunny.emergencyresponder.AUTO_RESPONSE_EXPIRED";

	Spinner respondForSpinner;
	CheckBox locationCheckbox;
	EditText responseTextBox;

	PendingIntent intentToFire;
	
	private BroadcastReceiver stopAutoBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(alarmAction)) {
				String preferenceName = getString(R.string.user_preferences);
				SharedPreferences sp = getSharedPreferences(preferenceName, 0);
				
				Editor editor = sp.edit();
				editor.putBoolean(autoResponsePref, false);
				editor.commit();
			}
		}
		
	};
	
	private void setAlarm(int respondForIndex) {
		// 创建警报，并注册报警的Intent
		AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
		
		if (intentToFire == null) {
			Intent intent = new Intent(alarmAction);
			intentToFire = PendingIntent
					.getBroadcast(getApplicationContext(), 0, intent, 0);
			IntentFilter filter = new IntentFilter(alarmAction);
			registerReceiver(stopAutoBroadcastReceiver, filter);
		}
		
		if (respondForIndex < 1)
			// 如果选择了disabled，则取消警报
			alarms.cancel(intentToFire);
		else {
			// 否则找出选项所代表的时长，并将警报设置为该段时间过去后触发
			Resources r = getResources();
			int[] respondForValues = r.getIntArray(R.array.respondForValues);
			int respondFor = respondForValues[respondForIndex];
			
			long t = System.currentTimeMillis();
			t += respondFor * 1000 * 60;
			
			// 设置警报
			alarms.set(AlarmManager.RTC_WAKEUP, t, intentToFire);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.autoresponder);
		
		respondForSpinner = (Spinner) findViewById(R.id.spinnerRespondFor);
		locationCheckbox = (CheckBox) findViewById(R.id.checkboxLocation);
		responseTextBox = (EditText) findViewById(R.id.responseText);
		
		ArrayAdapter<CharSequence> adapter = 
				ArrayAdapter.createFromResource(this, R.array.respondForDisplayItems, 
						android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		respondForSpinner.setAdapter(adapter);
		
		Button okButton = (Button) findViewById(R.id.okButton);
		okButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				savePreferences();
				setResult(RESULT_OK, null);
				finish();
			}
		});
		
		Button cancelButton = (Button) findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				respondForSpinner.setSelection(-1);
				savePreferences();
				setResult(RESULT_CANCELED, null);
				finish();
			}
		});
		
		// 加载已经保存的偏好数据并更新UI
		updateUIFromPreferences();
	}

	private void updateUIFromPreferences() {
		// 获取保存设置
		String preferenceName = getString(R.string.user_preferences);
		SharedPreferences sp = getSharedPreferences(preferenceName, 0);
		
		boolean autoRespond = sp.getBoolean(autoResponsePref, false);
		String respondText = sp.getString(responseTextPref, defaultResponseText);
		boolean includeLoc = sp.getBoolean(includeLocPref, false);
		int respondForIndex = sp.getInt(respondForPref, 0);
		
		// 对UI应用已保存的设置
		if (autoRespond)
			respondForSpinner.setSelection(respondForIndex);
		else
			respondForSpinner.setSelection(0);
		locationCheckbox.setChecked(includeLoc);
		responseTextBox.setText(respondText);
	}

	protected void savePreferences() {
		// 从UI中获取当前设置
		boolean autoRespond = 
				respondForSpinner.getSelectedItemPosition() > 0;
		int respondForIndex = respondForSpinner.getSelectedItemPosition();
		boolean includeLoc = locationCheckbox.isChecked();
		String respondText = responseTextBox.getText().toString();
		
		// 保存到共享首选项文件
		String preferenceName = getString(R.string.user_preferences);
		SharedPreferences sp = getSharedPreferences(preferenceName, 0);
		
		Editor editor = sp.edit();
		editor.putBoolean(autoResponsePref, autoRespond);
		editor.putString(responseTextPref, respondText);
		editor.putBoolean(includeLocPref, includeLoc);
		editor.putInt(respondForPref, respondForIndex);
		editor.commit();
		
		// 设置警报以关闭自动响应程序
		setAlarm(respondForIndex);
	}

}
