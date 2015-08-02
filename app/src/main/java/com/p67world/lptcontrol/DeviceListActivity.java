package com.p67world.lptcontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Created by Gilles on 02/08/2015.
 */
public class DeviceListActivity extends Activity{

    // Liste des devices
    private ArrayAdapter<String> g_devicesArrayAdapter;

    // Bluetooth adapter
    private BluetoothAdapter g_btAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.devicelist);

        // Initialiser le bouton pour faire l'analyse
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        // Création de l'adapter array pour la liste des devices
        g_devicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.devicename);

        // Générer la liste view
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(g_devicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(g_devicesClickListener);

        // Enregistrement pour broadcaster lorsqu'un device est découvert
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(g_receiver, filter);

        // Enregistement pour broadcaster une fois la découverte terminée
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(g_receiver, filter);

        // Récupération de l'adaptateur local
        g_btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // On s'assurer qu'il n'y a plus de découverte de devices en cours
        if(g_btAdapter != null){
            g_btAdapter.cancelDiscovery();
        }

        // Unregister
        this.unregisterReceiver(g_receiver);
    }

    // Lancement de la découverte de devices
    private void doDiscovery(){
        // Si découverte déjà en cours, on l'arrête
        if(g_btAdapter.isDiscovering()){
            g_btAdapter.cancelDiscovery();
        }

        // On lance la découverte
        g_btAdapter.startDiscovery();
    }

    // On click listener pour les devices de la listview
    private AdapterView.OnItemClickListener g_devicesClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            g_btAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra("device_address", address);

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    // Broadcast receiver
    private final BroadcastReceiver g_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Ajout du device
                if(device.getName().contains("RNBT") || device.getName().contains("RN42")){
                    g_devicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }


                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle("Cliquer sur le LPT a connecter");
                if (g_devicesArrayAdapter.getCount() == 0) {
                    String noDevices = "Aucun peripherique trouve".toString();
                    g_devicesArrayAdapter.add(noDevices);
                }
            }
        }
    };
}
