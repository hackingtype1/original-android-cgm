package com.ht1.cc.cgm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import com.ht1.cc.USB.UsbSerialDriver;
import com.ht1.cc.USB.UsbSerialProber;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;

//Still kludgy
//Newer, similar to Dex's architecture, classes are in progress, but this works reliably, if not the 
//most efficient, elegant design around.

public class DexcomReader extends AsyncTask<UsbSerialDriver, Object, Object>{
	
	private UsbSerialDriver mSerialDevice;
	public String bGValue;
	public String displayTime;
	public String trend;
	
	public DexcomReader (UsbSerialDriver device)
	{
		mSerialDevice = device;
	}
	public DexcomReader ()
	{

	}
	
	//The basic flow
	//for my requirements, I only care about the most recent data
	//Dex grabs 4 pages of data at a time, so I parse the last 4 pages
	//save the data to CSV and the most recent data to a serialized object for the
	//gui to quickly ingest
	public void readFromReceiver(Context context) {
        
		//locate the EGV data pages
        byte[] dexcomPageRange = getEGVDataPageRange();
        //Get the last 4 pages
        byte[] databasePages = getLastFourPages(dexcomPageRange);
        //Parse 'dem pages
        EGVRecord[] mostRecentData = parseDatabasePages(databasePages);
        
        //save them to the android file system for late access
		writeLocalCSV(mostRecentData, context);
        
	}
	
	//Not being used, but this is a nice to have if we want to kill the receiver, etc from
	//UI
	public void shutDownReceiver(Context context){

		UsbManager manager = (UsbManager) context
				.getSystemService(Context.USB_SERVICE);
		UsbSerialDriver mSerialDevice = UsbSerialProber.acquire(manager);
		if (mSerialDevice != null) {
			try {
				mSerialDevice.open();
				// EGVData page range read command
				byte[] resetPacket = new byte[6];
				resetPacket[0] = 0x01;
				resetPacket[1] = 0x06;
				resetPacket[3] = 0x2e;
				resetPacket[4] = (byte) 0xb8;
				resetPacket[5] = (byte) 0x01;
				try {
					mSerialDevice.write(resetPacket, 200);
				} catch (IOException e) {
					// mTitleTextView.setText("Error Writing.");
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
		
	}
	
	private byte[] getEGVDataPageRange(){
    	int[] rets = new int[24];
        int c = 0;
        
        //EGVData page range read command
        byte[] readEGVDataPageRange = new byte[7];
        readEGVDataPageRange[0] = 0x01;
        readEGVDataPageRange[1] = 0x07;		
        readEGVDataPageRange[3] = 0x10;
        readEGVDataPageRange[4] = 0x04;
        readEGVDataPageRange[5] = (byte)0x8b;
        readEGVDataPageRange[6] = (byte)0xb8;
    
        try {
			rets[c++] = mSerialDevice.write(readEGVDataPageRange, 200);
		} catch (IOException e) {
			//mTitleTextView.setText("Error Writing.");
		}
        byte[] dexcomPageRange = new byte[256];
        try {
        	rets[c++] = mSerialDevice.read(dexcomPageRange, 200);
		} catch (IOException e) {
			//mTitleTextView.setText("Error Writing.");
		}
        
        return dexcomPageRange;
	}

	private byte[] getLastFourPages(byte [] dexcomPageRange)
	{
    	int[] rets = new int[24];
        int c = 0;
        byte [] endPage = new byte[]{dexcomPageRange[8], dexcomPageRange[9], dexcomPageRange[10], dexcomPageRange[11]};
        
        //ONLY interested in the last 4 pages of data for this app's requirements
        int endInt = toInt(endPage, 1);
        int lastFour = endInt-3;
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(lastFour);
        byte[] result = b.array();
          
        //Build get page (EGV) command
        byte [] getLastEGVPage = new byte[636];
        getLastEGVPage[0] = 0x01;
        getLastEGVPage[1] = 0x0c;
        getLastEGVPage[2] = 0x00;
        getLastEGVPage[3] = 0x11;
        getLastEGVPage[4] = 0x04;
        getLastEGVPage[5] = result[3];
        getLastEGVPage[6] = result[2];
        getLastEGVPage[7] = result[1];
        getLastEGVPage[8] = result[0];
        getLastEGVPage[9] = 0x04;
   
        //Get checksum
        int getLastEGVCRC = calculateCRC16(getLastEGVPage, 0, 10);    
        byte crcByte1 = (byte) (getLastEGVCRC & 0xff);
        byte crcByte2 = (byte) ((getLastEGVCRC >> 8) & 0xff);
        
        getLastEGVPage[10] = crcByte1;
        getLastEGVPage[11] = crcByte2;
                  
        try {

        	rets[c++] = mSerialDevice.write(getLastEGVPage, 200);
		} catch (IOException e) {
			
		}
        
        //Get pages
        byte[] dexcomDatabasePages = new byte[2122];

        try {
        	rets[c++] = mSerialDevice.read(dexcomDatabasePages, 20000);

		} catch (IOException e) {
			
		}
        
       //Parse pages
        byte [] databasePages = new byte[2112];
        System.arraycopy(dexcomDatabasePages, 4, databasePages, 0, 2112);
        return databasePages;        
	}

    private EGVRecord[] parseDatabasePages(byte[] databasePages) {

        byte [][] fourPages = new byte[4][528];
        int [] recordCounts = new int[4];
        int totalRecordCount = 0;
        
        //we parse 4 pages at a time, calculate total record count while we do this
        for (int i = 0; i < 4; i++)
        {
        	System.arraycopy(databasePages, 528*i, fourPages[i], 0, 528);
        	recordCounts[i] = fourPages[i][4];
        	totalRecordCount = totalRecordCount + recordCounts[i];
        }
        
        EGVRecord[] recordsToReturn = new EGVRecord[totalRecordCount];
        int k = 0;
        
        //parse each record, plenty of room for improvement
        byte [] tempRecord = new byte[13];
        for (int i = 0; i < 4; i++)
        {
        	for (int j = 0; j < recordCounts[i]; j++)
        	{
        		System.arraycopy(fourPages[i], 28 + j*13, tempRecord, 0, 13);
        		
        		byte [] eGValue = new byte[]{tempRecord[8],tempRecord[9]};

                int bGValue = ((eGValue[1]<<8) + (eGValue[0] & 0xff)) & 0x3ff;
                   
                byte [] dateTime = new byte[]{tempRecord[7],tempRecord[6],tempRecord[5],tempRecord[4]};
                
                ByteBuffer buffer = ByteBuffer.wrap(dateTime);
                int dt = buffer.getInt();//*1000;

                String string_date = "1-January-2009";
                SimpleDateFormat f = new SimpleDateFormat("dd-MMM-yyyy");
                Date d;
				try {
					d = f.parse(string_date);
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					d = new Date();
				}
                long milliseconds = d.getTime();
                
                long timeAdd = milliseconds + (1000L*dt);
                TimeZone tz = TimeZone.getDefault();
                
                if (tz.inDaylightTime(new Date()))
                	timeAdd = timeAdd - 3600000L;
                
        		Date display = new Date(timeAdd);
        		
        		byte trendArrow = (byte) (tempRecord[10] & (byte)15);
        		String trend = "Not Calculated";
        		String trendA = "--X";
        		
        		switch (trendArrow) {	

        		case (0):
        			trendA = "\u2194";
        			trend = "NONE";
        			break;
        		case (1):
        			trendA = "\u21C8";
        			trend = "DoubleUp";
        			break;
        		case (2):
        			trendA = "\u2191";
        			trend = "SingleUp";
        			break;
        		case (3):
        			trendA = "\u2197";
        			trend = "FortyFiveUp";
        			break;
        		case (4):
        			trendA = "\u2192";
        			trend = "Flat";
        			break;
        		case (5):
        			trendA = "\u2198";
        			trend = "FortyFiveDown";
        			break;
        		case (6):
        			trendA = "\u2193";
        			trend = "SingleDown";
        			break;
        		case (7):
        			trendA = "\u21CA";
        			trend = "DoubleDown";
        			break;
        		case (8):
        			trendA = "\u2194";
        			trend = "NOT COMPUTABLE";
        			break;
        		case (9):
        			trendA = "\u2194";
        			trend = "RATE OUT OF RANGE";
        			break;
        		}
                
        		this.trend = trend;
        		this.displayTime = new SimpleDateFormat("MM/dd/yyy hh:mm:ss aa").format(display);
        		this.bGValue = String.valueOf((int)bGValue);
        		
        		EGVRecord record = new EGVRecord();
        		record.setBGValue(this.bGValue);
        		record.setDisplayTime(this.displayTime);
        		record.setTrend(this.trend);
        		record.setTrendArrow(trendA);
        		
        		recordsToReturn[k++] = record;
        	}
        }       
		return recordsToReturn;
		
	}

	private void writeLocalCSV(EGVRecord[] mostRecentData, Context context) {
		
		//Write EGV Binary of last (most recent) data
		try
        {
        	ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(context.getFilesDir(), "save.bin"))); //Select where you wish to save the file...
        	oos.writeObject(mostRecentData[mostRecentData.length - 1]); // write the class as an 'object'
        	oos.flush(); // flush the stream to insure all of the information was written to 'save.bin'
        	oos.close();// close the stream
        }
        catch(Exception ex)
        {
        	ex.printStackTrace();
        }
        
		//Write CSV of EGV from last 4 pages
        CSVWriter writer;
		try {
			
			writer = new CSVWriter(new FileWriter(new File(context.getFilesDir(), "data.csv")),',', CSVWriter.NO_QUOTE_CHARACTER);
	        List<String[]> data = new ArrayList<String[]>();
	        data.add(new String[] {"GlucoseValue","DisplayTime"});
	        
	        for (int i = 0; i < mostRecentData.length; i++)
	        {
	        	data.add(new String[] {mostRecentData[i].bGValue, mostRecentData[i].displayTime});
	        }
	         
	        writer.writeAll(data);
	         
	        writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}   		
	}
	
	//CRC methods
	public static int calculateCRC16 (byte [] buff, int start, int end) {
    	
        int crc = 0;
        for (int i = start; i < end; i++)
        {
        	
            crc = ((crc  >>> 8) | (crc  << 8) )& 0xffff;
            crc ^= (buff[i] & 0xff);
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xFF) << 5) & 0xffff;

        }
        crc &= 0xffff;
        return crc;
    	
    }
    
	//Convert the packet data
	public static int toInt(byte[] b, int flag) {
		switch(flag){
			case 0: //BitConverter.FLAG_JAVA:
				return (int)(((b[0] & 0xff)<<24) | ((b[1] & 0xff)<<16) | ((b[2] & 0xff)<<8) | (b[3] & 0xff));
			case 1: //BitConverter.FLAG_REVERSE:
				return (int)(((b[3] & 0xff)<<24) | ((b[2] & 0xff)<<16) | ((b[1] & 0xff)<<8) | (b[0] & 0xff));
			default:
				throw new IllegalArgumentException("BitConverter:toInt");
		}
	}
	
	public static byte[] getBytes(int i, int flag) {
		byte[] b = new byte[4];
		switch (flag) {
		case 0:
			b[0] = (byte) ((i >> 24) & 0xff);
			b[1] = (byte) ((i >> 16) & 0xff);
			b[2] = (byte) ((i >> 8) & 0xff);
			b[3] = (byte) (i & 0xff);
			break;
		case 1:
			b[3] = (byte) ((i >> 24) & 0xff);
			b[2] = (byte) ((i >> 16) & 0xff);
			b[1] = (byte) ((i >> 8) & 0xff);
			b[0] = (byte) (i & 0xff);
			break;
		default:
			break;	
		}
		return b;
	}

	@Override
	protected Object doInBackground(UsbSerialDriver... params) {
		
		return new String[]{displayTime, bGValue, trend}; 
		
	}



}
