package com.ht1.cc.cgm;

import java.io.IOException;

import com.ht1.cc.USB.SerialInputOutputManager;
import com.ht1.cc.USB.USBPower;
import com.ht1.cc.USB.UsbSerialDriver;
import com.ht1.cc.USB.UsbSerialProber;
import com.ht1.cc.upload.UploadHelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DexcomG4Service extends Service {

	private final String TAG = DexcomG4Activity.class.getSimpleName();

	/**
	 * The device currently in use, or {@code null}.
	 */
	private UsbSerialDriver mSerialDevice;

	/**
	 * The system's USB service.
	 */
	public UsbManager mUsbManager;
	private UploadHelper uploader;
	private Handler mHandler = new Handler();

	private SerialInputOutputManager mSerialIoManager;
	private WifiManager wifiManager;

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate called");
		wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		// connectToG4();
		mHandler.removeCallbacks(readAndUpload);
		mHandler.post(readAndUpload);
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called, will we recover?");
		mHandler.removeCallbacks(readAndUpload);
		USBOn();

		try {
			doReadAndUpload();
		} catch (Exception e) {
			Log.e(TAG, "onDestroy doReadAndUpload failed", e);
		}

		USBOn();

		stopIoManager();

		if (mSerialDevice != null) {
			try {
				mSerialDevice.close();
			} catch (IOException e) {
				Log.e(TAG, "Unable to readAndUpload", e);
			}
			mSerialDevice = null;
		}
		
	}

	//get the data upload it
	//if you don't have root, the On, Off commands won't do a thing - shouldn't break anything either
	private Runnable readAndUpload = new Runnable() {
		public void run() {

		try {
			uploader = new UploadHelper(getBaseContext());
			if (isConnected() && isOnline()) {

				USBOn();
				try {
					doReadAndUpload();
				} catch (Exception e) {
					Log.e(TAG, "readAndUpload doReadAndUpload failed, caught exception so read will be scheduled again", e);
				}
				USBOff();

				//displayMessage("Upload Complete");

			} else {
				USBOn();
				USBOff();
				Log.i(TAG, "Upload Fail");
				displayMessage("Upload Fail");
			}

		} catch (Exception e) {
			// ignore... for now - simply prevent service and activity from
			// losing its shit.
			USBOn();
			Log.e(TAG, "Unable to readAndUpload", e);
		}

		mHandler.postDelayed(readAndUpload, 45000);
		}
	};

	private void acquireSerialDevice() {
		mSerialDevice = null;
		mSerialDevice = UsbSerialProber.acquire(mUsbManager);
		if (mSerialDevice == null) {

			Log.i(TAG, "Unable to get the mSerialDevice, forcing USB PowerOn, and trying to get an updated mUsbManager");

			try {
				USBPower.PowerOn();
			} catch (Exception e) {
				Log.w(TAG, "acquireSerialDevice: Unable to PowerOn", e);
			}

			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) { }

			mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) { }

			mSerialDevice = UsbSerialProber.acquire(mUsbManager);
		}
	}

	protected void doReadAndUpload() throws Exception {

		try {

			acquireSerialDevice();

			if (mSerialDevice != null) {
				startIoManager();
				mSerialDevice.open();

				//Go get the data
				DexcomReader dexcomReader = new DexcomReader(mSerialDevice);
				dexcomReader.readFromReceiver(getBaseContext());

				uploader.execute(dexcomReader.displayTime,
						dexcomReader.bGValue, dexcomReader.trend);

				Handler handler = new Handler();
				handler.postDelayed(new Runnable() {
					@Override
					//Interesting case: location with lousy wifi
					//toggle it off to use cellular
					//toggle back on for next try
					public void run() {
						Status dataUp = uploader.getStatus();
						if (dataUp == AsyncTask.Status.RUNNING) {
							uploader.cancel(true);
							
							if (wifiManager.isWifiEnabled()) {
								wifiManager.setWifiEnabled(false);

								try {
									Thread.sleep(2500);
								} catch (InterruptedException e) { }

								wifiManager.setWifiEnabled(true);

								try {
									Thread.sleep(2500);
								} catch (InterruptedException e) { }
							}
						}

					}
				}, 22500);

			}

		} catch (Exception e) {
			Log.e(TAG, "Unable to doReadAndUpload", e);
			throw e;
		}

	}

	private void USBOff() {
		if (mSerialDevice != null) {
			try {
				mSerialDevice.close();
			} catch (IOException e) {
				Log.w(TAG, "Unable to close mSerialDevice", e);
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) { }

			try {
				USBPower.PowerOff();
			} catch (Exception e) {
				Log.w(TAG, "Unable to PowerOff, maybe set mSerialDevice to null?", e);
			}
		} else {
			Log.i(TAG, "USBOff: Receiver Not Found");
			// displayMessage("Receiver Not Found");
			// android.os.Process.killProcess(android.os.Process.myPid());
		}

	}

	private void USBOn() {

		acquireSerialDevice();

		if (mSerialDevice != null) {
			try {
				mSerialDevice.close();
			} catch (IOException e) {
				Log.w(TAG, "Unable to close mSerialDevice", e);
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) { }

			try {
				USBPower.PowerOn();
			} catch (Exception e) {
				Log.w(TAG, "Unable to PowerOn", e);
			}
		} else {
			Log.i(TAG, "USBOn: Receiver Not Found");
			// displayMessage("Receiver Not Found");
			// android.os.Process.killProcess(android.os.Process.myPid());
		}

	}

	private boolean isConnected() {

		acquireSerialDevice();

		if (mSerialDevice == null) {
			Log.w(TAG, "Receiver Not Found");
			//displayMessage("CGM Not Found...");
			//this.stopSelf(); //Jason: I think this was the main cause of instability
			return false; // yeah, I know
		} 
		return true;

	}

	private boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnectedOrConnecting()) {
			return true;
		}
		return false;
	}

	private void displayMessage(String message) {
		Toast toast = Toast.makeText(getBaseContext(), message,
				Toast.LENGTH_LONG);
		toast.setGravity(Gravity.CENTER, 0, 0);
		LinearLayout toastLayout = (LinearLayout) toast.getView();
		TextView toastTV = (TextView) toastLayout.getChildAt(0);
		toastTV.setTextSize(20);
		toastTV.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
		toast.show();

	}

	private void stopIoManager() {
		if (mSerialIoManager != null) {
			Log.i(TAG, "Stopping io manager ..");
			mSerialIoManager.stop();
			mSerialIoManager = null;
		}
	}

	private void startIoManager() {
		if (mSerialDevice != null) {
			Log.i(TAG, "Starting io manager ..");
			mSerialIoManager = new SerialInputOutputManager(mSerialDevice);
			// mExecutor.submit(mSerialIoManager);
		}
	}

}
