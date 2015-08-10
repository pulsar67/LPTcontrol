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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.os.Handler;

import org.w3c.dom.Text;


public class MainActivity extends FragmentActivity implements ActionBar.TabListener {
    // LPT protocol
    private static final byte LPT_SET_SENSITIVITY_MIN = 1;
    private static final byte LPT_SET_SENSITIVITY_MAX = 2;
    private static final byte LPT_SET_INHIBITION =		3;
    private static final byte LPT_SET_LAG =				4;
    private static final byte LPT_SET_SENSOR =			5;
    private static final byte LPT_SET_START_LAG =		6;
    private static final byte LPT_SET_MANUAL_SHUTTER =	7;
    private static final byte LPT_SET_MANUAL_STOP =		8;
    private static final byte LPT_SET_PWM_LED =			9;
    private static final byte LPT_SET_INTER_NB_POSES =	10;
    private static final byte LPT_SET_INTER_SHUTTER =	11;
    private static final byte LPT_SET_INTER_INTERVAL =	12;
    private static final byte LPT_SET_INTER_DELAY =		13;
    private static final byte LPT_SET_INTER_START =		14;
    private static final byte LPT_SET_INTER_STOP =		15;
    private static final byte LPT_SET_CUR_SENSITIVITY = 16;
    private static final byte LPT_SET_PREFOCUS =		17;

    private static final byte LPT_GET_CHARGING =		(byte)128;
    private static final byte LPT_GET_SENSITIVITY_MIN = (byte)129;
    private static final byte LPT_GET_SENSITIVITY_MAX = (byte)130;
    private static final byte LPT_GET_INHIBITION =		(byte)131;
    private static final byte LPT_GET_LAG =				(byte)132;
    private static final byte LPT_GET_SENSOR =			(byte)133;
    private static final byte LPT_GET_FW_VERSION =		(byte)134;
    private static final byte LPT_GET_PWM_LED =			(byte)135;
    private static final byte LPT_GET_INTER_NB_POSES =	(byte)136;
    private static final byte LPT_GET_INTER_SHUTTER =	(byte)137;
    private static final byte LPT_GET_INTER_INTERVAL =	(byte)138;
    private static final byte LPT_GET_INTER_DELAY =		(byte)139;
    private static final byte LPT_GET_INTER_CURR_STAT =	(byte)140; // Indique si l'intervallomètre est démarré, si on a une pose en cours ou si
    private static final byte LPT_GET_INTER_CURR_NB =	(byte)141;
    private static final byte LPT_GET_CUR_SENSITIVITY = (byte)142;
    private static final byte LPT_GET_PREFOCUS =		(byte)143;
    private static final byte LPT_GET_BATT_LVL =		(byte)144;
    private static final byte LPT_GET_STRIKE_NB =		(byte)145;
    private static final byte LPT_GET_STRIKE_VAL =		(byte)146;

    private static final byte LPT_INTER_STATE_INACTIVE =	1;
    private static final byte LPT_INTER_STATE_WAIT_START =	2;
    private static final byte LPT_INTER_STATE_WAIT_NEXT =	4;
    private static final byte LPT_INTER_STATE_ACTIVE =		3;

    // Request codes
    private static final int REQUEST_CONNECT_BT = 1;

    private Menu g_menu;
    private static boolean g_bConnected = false;

    // Adaptateur permet de récupérer le fragment
    AppSectionsPagerAdapter g_pagerAdapter;

    // Permet de lier l'onglet au fragment concerner
    ViewPager g_viewPager;

    // Socket Bluetooth
    private static BluetoothCom g_btCom;

    private BluetoothAdapter g_btAdapter = BluetoothAdapter.getDefaultAdapter();

    private long lastTime = 0;
    private byte[] g_ReceivedBuffer = new byte[6];
    private int g_iBufferPos = 0;

    private void manageCom(byte[] data){
        switch(data[0]){
            case LPT_GET_CUR_SENSITIVITY:
                SeekBar seekSens = (SeekBar)findViewById(R.id.seekSensitivity);
                seekSens.setProgress(g_ReceivedBuffer[4]);
                break;
            case LPT_GET_PWM_LED:
                SeekBar seekLum = (SeekBar)findViewById(R.id.seekLedLuminosity);
                seekLum.setProgress(g_ReceivedBuffer[4]);
                break;
            case LPT_GET_PREFOCUS:
                CheckBox checkPrefocus = (CheckBox)findViewById(R.id.checkPrefocus);
                checkPrefocus.setChecked(g_ReceivedBuffer[4]==1);
                break;
        }
    }

    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            byte[] data = msg.getData().getByteArray("receivedData");

            // Si on rentre ici avec un buffer nul, on réinitialise le dernier comptage de temps
            long t = System.currentTimeMillis();
            if(g_iBufferPos == 0) lastTime = System.currentTimeMillis();
            // Sinon, si on attend depuis trop longtemps, on réinitialise le buffer
            else if(t-lastTime > 500){
                g_iBufferPos = 0;
            }

            for(byte b:data){
                // On ajout chaque octet reçu
                g_ReceivedBuffer[g_iBufferPos++] = b;

                // Si on en a 6, on a probablement une trame complète
                if(g_iBufferPos == 6){
                    // Pour la console
                    StringBuilder sb = new StringBuilder(g_ReceivedBuffer.length * 2);
                    for(byte d:g_ReceivedBuffer)sb.append(String.format("%02x ", d & 0xff));
                    Log.d("handlemessage", sb.toString());

                    // On traite
                    manageCom(g_ReceivedBuffer);
                    g_iBufferPos = 0;
                }
            }
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
                //g_btCom.sendCommand(LPT_GET_CUR_SENSITIVITY, 0);
                //g_btCom.sendCommand(LPT_GET_PREFOCUS, 0);
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

        if(!g_bConnected) {
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
                    g_btAdapter.cancelDiscovery();

                    // Création du BluetoothCom
                    g_btCom = new BluetoothCom(handlerStatus, handler);

                    // On connecte et on lance
                    BluetoothDevice device = g_btAdapter
                            .getRemoteDevice(data.getExtras()
                                    .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS));
                    g_btCom.connect(device);
                }
        }
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
        SeekBar g_seekLedLum = null;
        SeekBar g_seekSensitivity = null;
        SeekBar g_seekInhibit = null;
        CheckBox g_checkPrefocus = null;

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View l_view = inflater.inflate(R.layout.fragment_general, container, false);

            // Luminosité LEDs
            g_seekLedLum = (SeekBar)l_view.findViewById(R.id.seekLedLuminosity);
            g_seekLedLum.setMax(10);
            g_seekLedLum.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.valLedLum);
                    String val = Integer.toString(progress*10) + "%";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 9, seekBar.getProgress() * 10);
                }
            });

            // Sensibilité
            g_seekSensitivity = (SeekBar)l_view.findViewById(R.id.seekSensitivity);
            g_seekSensitivity.setMax(4);
            if(g_bConnected){
                g_btCom.sendCommand(LPT_GET_CUR_SENSITIVITY, 0);
            }

            g_seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.valSensitivity);
                    text.setText(Integer.toString(progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 16, seekBar.getProgress());
                }
            });

            // Inhibition
            g_seekInhibit = (SeekBar)l_view.findViewById(R.id.seekInhibition);
            g_seekInhibit.setMax(100);
            g_seekInhibit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.valInhibit);
                    String val = Integer.toString(progress*100) + "ms";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 3, seekBar.getProgress()*100);
                }
            });

            // Préfocus
            g_checkPrefocus = (CheckBox)l_view.findViewById(R.id.checkPrefocus);
            if(g_bConnected){
                g_btCom.sendCommand(LPT_GET_PREFOCUS, 0);
            }
            g_checkPrefocus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 17, isChecked?1:0);
                }
            });

            return l_view;
        }
    }

    public static class InterFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View l_view = inflater.inflate(R.layout.fragment_inter, container, false);

            // Seekbars de réglages
            SeekBar g_seekInterStart = (SeekBar)l_view.findViewById(R.id.seekInterStart);
            g_seekInterStart.setMax(999);
            g_seekInterStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterStart);
                    String val = Integer.toString(progress) + " s";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            SeekBar g_seekInterShutter = (SeekBar)l_view.findViewById(R.id.seekInterShutter);
            g_seekInterShutter.setMax(999);
            g_seekInterShutter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterShutter);
                    String val = Integer.toString(progress) + " s";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            SeekBar g_seekInterInterval = (SeekBar)l_view.findViewById(R.id.seekInterInterval);
            g_seekInterInterval.setMax(999);
            g_seekInterInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterInterval);
                    String val = Integer.toString(progress) + " s";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            SeekBar g_seekInterNumber = (SeekBar)l_view.findViewById(R.id.seekInterNumber);
            g_seekInterNumber.setMax(999);
            g_seekInterNumber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.txtInterNumber);
                    text.setText(Integer.toString(progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            return l_view;
        }
    }

    public static class LagFragment extends Fragment {
        Button g_btnLag = null;
        int g_iLagVal = 0;
        TextView g_txtRes = null;

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View l_view = inflater.inflate(R.layout.fragment_lag, container, false);

            // Bouton lancement lag
            g_btnLag = (Button)l_view.findViewById(R.id.btnStartLag);
            g_btnLag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 6, 0);
                }
            });

            // Checkboxes
            g_txtRes = (TextView)l_view.findViewById(R.id.txtResult);
            CheckBox g_check0 = (CheckBox)l_view.findViewById(R.id.checkBox0);
            g_check0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?1:-1;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check1 = (CheckBox)l_view.findViewById(R.id.checkBox1);
            g_check1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?2:-2;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check2 = (CheckBox)l_view.findViewById(R.id.checkBox2);
            g_check2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?4:-4;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check3 = (CheckBox)l_view.findViewById(R.id.checkBox3);
            g_check3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?8:-8;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check4 = (CheckBox)l_view.findViewById(R.id.checkBox4);
            g_check4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?16:-16;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check5 = (CheckBox)l_view.findViewById(R.id.checkBox5);
            g_check5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?32:-32;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check6 = (CheckBox)l_view.findViewById(R.id.checkBox6);
            g_check6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?64:-64;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            CheckBox g_check7 = (CheckBox)l_view.findViewById(R.id.checkBox7);
            g_check7.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?128:-128;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            return l_view;
        }
    }
}
