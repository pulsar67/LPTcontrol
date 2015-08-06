package com.p67world.lptcontrol;


import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.os.Handler;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener {

    // Request codes
    private static final int REQUEST_CONNECT_BT = 1;

    private Menu g_menu;
    private boolean g_bConnected = false;

    // Adaptateur permet de récupérer le fragment
    AppSectionsPagerAdapter g_pagerAdapter;

    // Permet de lier l'onglet au fragment concerner
    ViewPager g_viewPager;

    // Socket Bluetooth
    private BluetoothCom g_btCom;

    private BluetoothAdapter g_btAdapter = BluetoothAdapter.getDefaultAdapter();

    private long lastTime = 0;

    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            String data = msg.getData().getString("receivedData");
            Log.d("handlemessage", data);
        }
    };

    final Handler handlerStatus = new Handler() {
        public void handleMessage(Message msg) {
            int co = msg.arg1;
            if(co == 1) {
                // On change l'icone du bouton
                MenuItem btn = g_menu.findItem(R.id.connectBtn);
                btn.setIcon(R.drawable.bt_icon_c);
                g_bConnected = true;
                Log.d("handlemessage", "Connected");
            } else if(co == 2){
                // On change l'icone du bouton
                MenuItem btn = g_menu.findItem(R.id.connectBtn);
                btn.setIcon(R.drawable.bt_icon_n);
                g_bConnected = true;
                Log.d("handlemessage", "Disconnected");
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Création du pager adapter
        g_pagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();

        // Ne pas mettre les boutons home/up
        actionBar.setHomeButtonEnabled(false);

        // Indiquer qu'on va afficher des onglets
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Configuration du viewPager
        g_viewPager = (ViewPager) findViewById(R.id.pager);
        g_viewPager.setAdapter(g_pagerAdapter);
        g_viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // Ajout des onglets
        actionBar.addTab(actionBar.newTab().setText("GENERAL").setTabListener(this));
        actionBar.addTab(actionBar.newTab().setText("INTER.").setTabListener(this));
        actionBar.addTab(actionBar.newTab().setText("LAG").setTabListener(this));

        // Vérification si on a le BT
        if(BluetoothAdapter.getDefaultAdapter() == null){
            Toast.makeText(getApplicationContext(), "Votre appareil ne dispose pas de Bluetooth!",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        g_menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.connectBtn) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ONGLETS
    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        g_viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    public void onBtClick(MenuItem item) {

        if(g_bConnected == false) {
            // On active le BT si nécessaire
            if (!g_btAdapter.isEnabled()) {
                g_btAdapter.enable();
                Toast.makeText(getApplicationContext(), "Le Bluetooth n'était pas actif et a été activé",
                        Toast.LENGTH_SHORT).show();
            }

            // On crée l'activité de scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_BT);
        }
        else {
            Toast.makeText(getApplicationContext(), "Vous êtes déjà connecté!",
                    Toast.LENGTH_SHORT).show();
        }

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode){
            case REQUEST_CONNECT_BT:
                // Si on retourne avec un device à connecter
                if(resultCode == Activity.RESULT_OK){
                    // Création du BluetoothCom
                    g_btCom = new BluetoothCom(handlerStatus, handler);

                    // On connecte et on lance
                    g_btCom.connect(data.getExtras()
                                        .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS));
                }
        }
    }

    public void onSeekclick(View view) {
        if(g_bConnected)
        {
            SeekBar s = (SeekBar)findViewById(R.id.ledluminosity);
            sendCommand((byte)9, s.getProgress());
        }
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
        g_btCom.sendBinaryData(data);
    }

    public static class AppSectionsPagerAdapter extends FragmentPagerAdapter {

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case 0:
                    // The first section of the app is the most interesting -- it offers
                    // a launchpad into the other demonstrations in this example application.
                    return new GeneralFragment();
                case 1:
                    return new InterFragment();
                default:
                    return new LagFragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }

    // Chargement du contenu
    public static class GeneralFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View l_view = inflater.inflate(R.layout.fragment_general, container, false);
            return l_view;
        }
    }

    public static class InterFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View l_view = inflater.inflate(R.layout.fragment_inter, container, false);
            return l_view;
        }
    }

    public static class LagFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View l_view = inflater.inflate(R.layout.fragment_lag, container, false);
            return l_view;
        }
    }
}
