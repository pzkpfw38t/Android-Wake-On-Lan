package com.teamgy.wakeonlan.sendWol;

import android.app.IntentService;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.teamgy.wakeonlan.utils.Config;
import com.teamgy.wakeonlan.utils.Tools;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class WOLService extends IntentService {

    private WifiManager wifiManager;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public WOLService(String name) {
        super(name);
    }

    public WOLService() {
        super("WOLService");

    }

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private InetAddress getBroadcastAddress() throws IOException {
        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    private DatagramSocket socket;
    @Override
    protected void onHandleIntent(Intent intent) {
        //foreach mac adress, send magic packet
        //magicpacket = FF * 6 + mac *16
        //http://support.amd.com/TechDocs/20213.pdf specification for magic packet

        try {
            int retryInteval = intent.getIntExtra("retryInteval",3);
            int retrySleep = intent.getIntExtra("retrySleep",1);
            int wolPort = intent.getIntExtra("wolPort",40000);

            socket = new DatagramSocket(wolPort);
            socket.setBroadcast(true);
            socket.setReuseAddress(true);
            ArrayList<String> macAdresses = intent.getStringArrayListExtra("macAdresses");



            if (macAdresses != null) {
                String wolHeader = "ffffffffffff";

                for (int i = 0; i < retryInteval; i++) {

                    for (String macAdress : macAdresses) {

                        macAdress = Tools.reformatMACInput(macAdress, true);

                        String macWolData = new String(new char[16]).replace("\0", macAdress); //repeat mac 16 times
                        byte[] data = hexStringToByteArray(wolHeader + macWolData); //6 bytes
                        DatagramPacket packet = new DatagramPacket(data, data.length, getBroadcastAddress(), 40000);
                        socket.send(packet);
                        Log.d("wol service:", "sent packet ");

                    }

                    try {
                        Log.d("wol", "sleeping");
                        //this is retrying because wifi might me iffy when user connects first time
                        Thread.sleep(retrySleep * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }


                socket.close();
            }
        } catch (IOException e) {

            //TODO notify user something failed
            e.printStackTrace();
            //socket.close();


        }
    }

}
