package com.ht1.cc.cgm;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* Main activity for the DexcomG4Activity program */
public class DexcomG4Activity extends Activity {


	private Handler mHandler = new Handler();

	private int maxRetries = 20;
	private int retryCount = 0;

	private TextView mTitleTextView;
	private TextView mDumpTextView;
	private ScrollView mScrollView;
	private Button b1;

	private final String TAG = DexcomG4Activity.class.getSimpleName();


	//All I'm really doing here is creating a simple activity to launch and maintain the service
	private Runnable updateDataView = new Runnable() {
		public void run() {

			if (!isMyServiceRunning()) {
				if (retryCount < maxRetries) {
					startService(new Intent(DexcomG4Activity.this,
							DexcomG4Service.class));
					mTitleTextView.setTextColor(Color.YELLOW);
					String message = "Starting service " + retryCount + "/" + maxRetries;
					mTitleTextView.setText(message);
					Log.i(TAG, message);
					try {
						Thread.sleep(retryCount * 250L);
					} catch (InterruptedException e) {}
					++retryCount;
				} else {
					mTitleTextView.setTextColor(Color.RED);
					mTitleTextView.setText("Unable to restart service");
					Log.i(TAG, "Unable to restart service, trying to recreate the activity");
					mHandler.removeCallbacks(updateDataView);
					recreate();
				}
			} else {
				mTitleTextView.setTextColor(Color.GREEN);
				mTitleTextView.setText("CGM Service Started");
				EGVRecord record = DexcomG4Activity.this
						.loadClassFile(new File(getBaseContext().getFilesDir(),
								"save.bin"));
				mDumpTextView.setTextColor(Color.WHITE);
				mDumpTextView.setText("\n" + record.displayTime + "\n"
						+ record.bGValue + "\n" + record.trendArrow + "\n");
			}
			mHandler.postDelayed(updateDataView, 30000);
		}
	};
	private ObjectInputStream ois;

	//Look for and launch the service, display status to user
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.adb);

		mTitleTextView = (TextView) findViewById(R.id.demoTitle);
		mDumpTextView = (TextView) findViewById(R.id.demoText);
		mScrollView = (ScrollView) findViewById(R.id.demoScroller);

		LinearLayout lnr = (LinearLayout) findViewById(R.id.container);

		b1 = new Button(this);

		mHandler.post(updateDataView);

		mTitleTextView.setTextColor(Color.YELLOW);
		mTitleTextView.setText("CGM Service Pending");

		b1.setText("Stop Uploading CGM Data");
		lnr.addView(b1);

		b1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (b1.getText() == "Stop Uploading CGM Data") {
					mHandler.removeCallbacks(updateDataView);
					stopService(new Intent(DexcomG4Activity.this,
							DexcomG4Service.class));
					b1.setText("Start Uploading CGM Data");
					mTitleTextView.setTextColor(Color.RED);
					mTitleTextView.setText("CGM Service Stopped");
					finish();

				} else {
					mHandler.removeCallbacks(updateDataView);
					mHandler.post(updateDataView);
					b1.setText("Stop Uploading CGM Data");
				}
			}
		});

	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onResume() {
		super.onResume();
		//Refresh the status
		EGVRecord record = this.loadClassFile(new File(this.getFilesDir(),
				"save.bin"));
		mDumpTextView.setTextColor(Color.WHITE);
		mDumpTextView.setText("\n" + record.displayTime + "\n" + record.bGValue
				+ "\n" + record.trendArrow + "\n");
	}

	//Check to see if service is running
	private boolean isMyServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (DexcomG4Service.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	//Deserialize the EGVRecord (most recent) value
	public EGVRecord loadClassFile(File f) {
		try {
			ois = new ObjectInputStream(
					new FileInputStream(f));
			Object o = ois.readObject();
			return (EGVRecord) o;
		} catch (Exception ex) {
			Log.e(TAG, " unable to loadEGVRecord", ex);
		}
		return new EGVRecord();
	}
}
