package com.ht1.cc.upload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.*;

import org.apache.commons.net.ftp.FTPClient;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
public class UploadHelper extends AsyncTask<String, Integer, Long> {

	Context context;
	
    public UploadHelper(Context context) {
        this.context = context;
    }
	protected Long doInBackground(String... data) {

		//SO, here is where you're going to want to decide 
		//how and where you want to send the "hayden.csv"

		//I send to both azure site (FTP) and a google spreadsheet - between the
		//two I get pretty close to 100% up-time

		//FTP Example::

		FTPClient ftpClient = new FTPClient();

		try {
			ftpClient.connect(InetAddress.getByName("[YOUR SERVER]"));
			ftpClient.login("USERNAME", "PASSWORD");
			ftpClient.changeWorkingDirectory("DIRECTORY");

			if (ftpClient.getReplyString().contains("250")) {
				ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
				BufferedInputStream buffIn = null;
				buffIn = new BufferedInputStream(new FileInputStream(new File(context.getFilesDir(), "hayden.csv")));
				ftpClient.enterLocalPassiveMode();

				ftpClient.storeFile("hayden.csv", buffIn);
				buffIn.close();
				ftpClient.logout();
				ftpClient.disconnect();
			}
		}

		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return 1L;
	}

	protected void onPostExecute(Long result) {
		super.onPostExecute(result);
		Log.i("Uploader", result + " Status: FINISHED");

	}

}
