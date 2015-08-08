package com.p67world.lptcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.os.Handler;

/**
 * Created by Gilles on 06/08/2015.
 */
public class BluetoothCom {

    private InputStream g_btReceiveStream = null;// Canal de réception
    private OutputStream g_btSendStream = null;// Canal d'émission
    private BluetoothDevice g_btDevice = null;
    private BluetoothSocket g_btSocket = null;
    private BluetoothAdapter g_btAdapter = null;    // Adapter Bluetooth

    private ReceiverThread g_btReceiverThread;

    Handler g_handler;

    public BluetoothCom(Handler hstatus, Handler h) {
        g_btAdapter = BluetoothAdapter.getDefaultAdapter();
        g_handler = hstatus;

        // Thread de réception
        g_btReceiverThread = new ReceiverThread(h);
    }

    public void connect(final String address){
        new Thread(){
            @Override
            public void run() {
                try{
                    // On récupère les infos du device
                    BluetoothDevice device = g_btAdapter.getRemoteDevice(address);
                    g_btSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    g_btReceiveStream = g_btSocket.getInputStream();
                    g_btSendStream = g_btSocket.getOutputStream();
                    g_btSocket.connect();
                    Message msg = g_handler.obtainMessage();
                    msg.arg1 = 1;
                    g_handler.sendMessage(msg);

                    g_btReceiverThread.start();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void close() {
        try {
            g_btSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BluetoothDevice getDevice() {
        return g_btDevice;
    }

    public void sendCommand(byte cmd, int value)
    {
        byte[] data = new byte[6];
        data[0] = cmd;
        data[1] = (byte)((int)value >> 24);
        data[2] = (byte)((int)value >> 16);
        data[3] = (byte)((int)value >> 8);
        data[4] = (byte)((int)value);
        data[5] = (byte)(data[0]+data[1]+data[2]+data[3]+data[4]);

        StringBuilder sb = new StringBuilder(data.length * 2);
        for(byte b:data)sb.append(String.format("%02x", b & 0xff));
        Log.d("MESSAGE SENT", sb.toString());
        sendBinaryData(data);
    }

    public void sendData(String data) {
        sendData(data, false);
    }

    public void sendBinaryData(byte[] data)
    {
        try {
            g_btSendStream.write(data);
            g_btSendStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendData(String data, boolean deleteScheduledData) {
        try {
            g_btSendStream.write(data.getBytes());
            g_btSendStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ReceiverThread extends Thread {
        Handler handler;

        ReceiverThread(Handler h) {
            handler = h;
        }

        @Override public void run() {
            while(true) {
                try {
                    if(g_btReceiveStream.available() > 0) {

                        byte buffer[] = new byte[100];
                        int k = g_btReceiveStream.read(buffer, 0, 100);

                        if(k > 0) {
                            byte rawdata[] = new byte[k];
                            for(int i=0;i<k;i++)
                                rawdata[i] = buffer[i];

                            String data = new String(rawdata);

                            Message msg = handler.obtainMessage();
                            Bundle b = new Bundle();
                            b.putString("receivedData", data);
                            msg.setData(b);
                            handler.sendMessage(msg);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
