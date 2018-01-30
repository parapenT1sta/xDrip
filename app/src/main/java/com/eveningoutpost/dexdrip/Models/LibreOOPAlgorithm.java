package com.eveningoutpost.dexdrip.Models;

import android.content.Intent;
import android.os.Bundle;

import com.eveningoutpost.dexdrip.LibreAlarmReceiver;
import com.eveningoutpost.dexdrip.NSEmulatorReceiver;
import com.eveningoutpost.dexdrip.xdrip;
import com.eveningoutpost.dexdrip.ImportedLibraries.usbserial.util.HexDump;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Services.TransmitterRawData;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.UtilityModels.Intents;
import com.eveningoutpost.dexdrip.UtilityModels.MockDataSource;
import com.eveningoutpost.dexdrip.UtilityModels.Pref;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static com.eveningoutpost.dexdrip.Models.BgReading.bgReadingInsertFromJson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;

public class LibreOOPAlgorithm {
    private static final String TAG = "LibreOOPAlgorithm";
    
    static public void SendData(byte[] fullData) {
        if(fullData == null) {
            Log.e(TAG, "SendData called with null data");
            return;
        }
        
        if(fullData.length < 344) {
            Log.e(TAG, "SendData called with data size too small. " + fullData.length);
            return;
        }
        Log.i(TAG, "Sending full data to OOP Algorithm data-len = " + fullData.length);
        
        fullData = java.util.Arrays.copyOfRange(fullData, 0, 0x158);
        Log.i(TAG, "Data that will be sent is " + HexDump.dumpHexString(fullData));
        
        Intent intent = new Intent(Intents.XDRIP_PLUS_LIBRE_DATA);
        Bundle bundle = new Bundle();
        bundle.putByteArray(Intents.LIBRE_DATA_BUFFER, fullData);
        bundle.putLong(Intents.LIBRE_DATA_TIMESTAMP, JoH.tsl());
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        xdrip.getAppContext().sendBroadcast(intent);
    }
    
    
    static public void HandleData(String oopData) {
        Log.e(TAG, "HandleData called with " + oopData);
        OOPResults oOPResults = null;
        try {
            final Gson gson = new GsonBuilder().create();
            oOPResults = gson.fromJson(oopData, OOPResults.class);
        } catch (Exception  e) { //TODO: what exception should we catch here.
            Log.e(TAG, "HandleData cought exception ", e);
            return;
        }
        boolean use_raw = Pref.getBooleanDefaultFalse("calibrate_external_libre_algorithm");
        ReadingData.TransferObject libreAlarmObject = new ReadingData.TransferObject();
        libreAlarmObject.data = new ReadingData();
        libreAlarmObject.data.trend = new ArrayList<GlucoseData>();
        
        double factor = 1;
        if(use_raw) {
            // When handeling raw, data is expected to be bigger in a factor of 1000 and 
            // is then devided by Constants.LIBRE_MULTIPLIER
            factor = 1000 / Constants.LIBRE_MULTIPLIER;
        }
        
        // Add the first object, that is the current time
        GlucoseData glucoseData = new GlucoseData();
        glucoseData.sensorTime = oOPResults.currentTime;
        glucoseData.realDate = oOPResults.timestamp;
        glucoseData.glucoseLevel = (int)(oOPResults.currentBg * factor);
        glucoseData.glucoseLevelRaw = (int)(oOPResults.currentBg * factor);
        
        libreAlarmObject.data.trend.add(glucoseData);
        
        // TODO: Add here data of last 10 minutes or whatever.
        
        
       // Add the historic data
        libreAlarmObject.data.history = new ArrayList<GlucoseData>();
        for(HistoricBg historicBg : oOPResults.historicBg) {
            if(historicBg.quality == 0) {
                glucoseData = new GlucoseData();
                glucoseData.realDate = oOPResults.timestamp + (historicBg.time - oOPResults.currentTime) * 60000;
                glucoseData.glucoseLevel = (int)(historicBg.bg * factor);
                glucoseData.glucoseLevelRaw = (int)(historicBg.bg * factor);
                libreAlarmObject.data.history.add(glucoseData);
            }
        }
        
        // Add the current point again. This is needed in order to have the last gaps closed.
        // TODO: Base this on real BG values.
        glucoseData = new GlucoseData();
        glucoseData.realDate = oOPResults.timestamp;
        glucoseData.glucoseLevel = (int)(oOPResults.currentBg * factor);
        glucoseData.glucoseLevelRaw = (int)(oOPResults.currentBg * factor);
        libreAlarmObject.data.history.add(glucoseData);
        
        
  
        Log.e(TAG, "HandleData Created the following object " + libreAlarmObject.toString());
        LibreAlarmReceiver.CalculateFromDataTransferObject(libreAlarmObject, use_raw);
        
    }
}
