package com.p67world.lptcontrol;


import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import android.os.Handler;

import org.w3c.dom.Text;


public class MainActivity extends FragmentActivity implements ActionBar.TabListener {
    // LPT protocol
    private static final byte LPT_SET_SENSITIVITY_MIN = 0x01;
    private static final byte LPT_SET_SENSITIVITY_MAX = 0x02;
    private static final byte LPT_SET_INHIBITION =		0x03;
    private static final byte LPT_SET_LAG =				0x04;
    private static final byte LPT_SET_SENSOR =			0x05;
    private static final byte LPT_SET_START_LAG =		0x06;
    private static final byte LPT_SET_MANUAL_SHUTTER =	0x07;
    private static final byte LPT_SET_MANUAL_STOP =		0x08;
    private static final byte LPT_SET_PWM_LED =			0x09;
    private static final byte LPT_SET_INTER_NB_POSES =	0x0A;
    private static final byte LPT_SET_INTER_SHUTTER =	0x0B;
    private static final byte LPT_SET_INTER_INTERVAL =	0x0C;
    private static final byte LPT_SET_INTER_DELAY =		0x0D;
    private static final byte LPT_SET_INTER_START =		0x0E;
    private static final byte LPT_SET_INTER_STOP =		0x0F;
    private static final byte LPT_SET_CUR_SENSITIVITY = 0x10;
    private static final byte LPT_SET_PREFOCUS =		0x11;

    private static final byte LPT_GET_CHARGING =		(byte)0x80;
    private static final byte LPT_GET_SENSITIVITY_MIN = (byte)0x81;
    private static final byte LPT_GET_SENSITIVITY_MAX = (byte)0x82;
    private static final byte LPT_GET_INHIBITION =		(byte)0x83;
    private static final byte LPT_GET_LAG =				(byte)0x84;
    private static final byte LPT_GET_SENSOR =			(byte)0x85;
    private static final byte LPT_GET_FW_VERSION =		(byte)0x86;
    private static final byte LPT_GET_PWM_LED =			(byte)0x87;
    private static final byte LPT_GET_INTER_NB_POSES =	(byte)0x88;
    private static final byte LPT_GET_INTER_SHUTTER =	(byte)0x89;
    private static final byte LPT_GET_INTER_INTERVAL =	(byte)0x8A;
    private static final byte LPT_GET_INTER_DELAY =		(byte)0x8B;
    private static final byte LPT_GET_INTER_CURR_STAT =	(byte)0x8C; // Indique si l'intervallomètre est démarré, si on a une pose en cours ou si
    private static final byte LPT_GET_INTER_CURR_NB =	(byte)0x8D;
    private static final byte LPT_GET_CUR_SENSITIVITY = (byte)0x8E;
    private static final byte LPT_GET_PREFOCUS =		(byte)0x8F;
    private static final byte LPT_GET_BATT_LVL =		(byte)0x90;
    private static final byte LPT_GET_STRIKE_NB =		(byte)0x91;
    private static final byte LPT_GET_STRIKE_VAL =		(byte)0x92;

    private static final byte LPT_INTER_STATE_INACTIVE =	0x01;
    private static final byte LPT_INTER_STATE_WAIT_START =	0x02;
    private static final byte LPT_INTER_STATE_WAIT_NEXT =	0x04;
    private static final byte LPT_INTER_STATE_ACTIVE =		0x03;

    // Request codes
    private static final int REQUEST_CONNECT_BT = 1;

    private Menu g_menu;
    private static boolean g_bConnected = false;

    // Adaptateur permet de récupérer le fragment
    AppSectionsPagerAdapter g_pagerAdapter;

    // Permet de lier l'onglet au fragment concerner
    NonSwipeableViewPager g_viewPager;

    // Socket Bluetooth
    private static BluetoothCom g_btCom;

    private BluetoothAdapter g_btAdapter = BluetoothAdapter.getDefaultAdapter();

    private long lastTime = 0;
    private byte[] g_ReceivedBuffer = new byte[6];
    private int g_iBufferPos = 0;
    private int g_iBattLvl = 0;
    private boolean g_bCharging = false;
    private boolean g_bInterActive = false;
    private Handler g_timeHandler = new Handler();
    private int g_iCurrentInterStatus = LPT_INTER_STATE_INACTIVE;
    private int g_iPreviousInterStatus = LPT_INTER_STATE_INACTIVE;
    private int g_iInterTimeCounter = 0;
    private static int g_iInterNbPoses = 0;
    private int g_iInterCurrPose = 0;
    private int g_iCurrentTab = 0;
    private boolean g_bExternalSensor = false;
    private boolean g_bIsLpt1 = false;

    // Elements graphiques
    // Onglet général
    private static SeekBar g_seekLedLum = null;
    private static SeekBar g_seekSensitivity = null;
    private static SeekBar g_seekInhibit = null;
    private static CheckBox g_checkPrefocus = null;
    private static CheckBox g_checkExtSensor = null;
    private static SeekBar g_seekExtDelay = null;

    // Onglet intervallomètre
    private static SeekBar g_seekInterStart = null;
    private static SeekBar g_seekInterShutter = null;
    private static SeekBar g_seekInterInterval = null;
    private static SeekBar g_seekInterNumber = null;
    private static Button g_btnInterStart = null;

    // Onglet lag
    private static Button g_btnLag = null;
    private static TextView g_txtRes = null;
    private static CheckBox g_check0 = null;
    private static CheckBox g_check1 = null;
    private static CheckBox g_check2 = null;
    private static CheckBox g_check3 = null;
    private static CheckBox g_check4 = null;
    private static CheckBox g_check5 = null;
    private static CheckBox g_check6 = null;
    private static CheckBox g_check7 = null;

    // Menu
    private static MenuItem g_btIcon = null;
    private static MenuItem g_lvlBattIcon = null;
    private static MenuItem g_firmTxt = null;
    private static MenuItem g_interStat = null;

    private void manageCom(byte[] data){
        byte[] l_bTempValue = { data[1], data[2], data[3], data[4]};
        int l_iTempValue = ByteBuffer.wrap(l_bTempValue).getInt();
        switch(data[0]){
            case LPT_GET_CHARGING:
                g_bCharging = l_iTempValue!=0?true:false;
                switch(g_iBattLvl){
                    case 1:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt1ch:R.drawable.batt1);
                        break;
                    case 2:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt2ch:R.drawable.batt2);
                        break;
                    case 3:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt3ch:R.drawable.batt3);
                        break;
                    case 4:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt4ch:R.drawable.batt4);
                        break;
                    default:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt0ch:R.drawable.batt0);
                        break;
                }
                break;
            case LPT_GET_SENSITIVITY_MIN:
                break;
            case LPT_GET_SENSITIVITY_MAX:
                break;
            case LPT_GET_INHIBITION:
                int iInhibitVal = l_iTempValue/100;
                g_seekInhibit.setProgress((iInhibitVal < 100) ? iInhibitVal:100);
                break;
            case LPT_GET_LAG:
                g_seekExtDelay.setProgress((l_iTempValue<100)?l_iTempValue:100);
                break;
            case LPT_GET_SENSOR:
                if(l_iTempValue == 0){
                    g_bExternalSensor = false;
                    g_checkExtSensor.setChecked(false);
                    g_seekExtDelay.setEnabled(false);
                }
                else{
                    g_bExternalSensor = true;
                    g_checkExtSensor.setChecked(true);
                    g_seekExtDelay.setEnabled(true);
                }
                break;
            case LPT_GET_FW_VERSION:
                //byte[] l_maj = {data[1], data[2]};
                int l_iMaj = (data[1] & 0xFF) * 10 | (data[2] & 0xFF);
                if(l_iMaj == 1) g_bIsLpt1 = true;
                else            g_bIsLpt1 = false;
                int l_iMin = (data[3] & 0xFF) * 10 | (data[4] & 0xFF);
                String str = "Firm.\nv" + Integer.toString(l_iMaj) + "." + Integer.toString(l_iMin);
                g_firmTxt.setTitle(str);
                break;
            case LPT_GET_PWM_LED:
                g_seekLedLum.setProgress(l_iTempValue);
                break;
            case LPT_GET_INTER_NB_POSES:
                g_seekInterNumber.setProgress(l_iTempValue);
                g_iInterNbPoses = l_iTempValue;
                break;
            case LPT_GET_INTER_SHUTTER:
                g_seekInterShutter.setProgress(l_iTempValue);
                break;
            case LPT_GET_INTER_INTERVAL:
                g_seekInterInterval.setProgress(l_iTempValue);
                break;
            case LPT_GET_INTER_DELAY:
                g_seekInterStart.setProgress(l_iTempValue);
                break;
            case LPT_GET_INTER_CURR_STAT:
                g_iCurrentInterStatus = l_iTempValue;

                switch(l_iTempValue){
                    case LPT_INTER_STATE_ACTIVE:
                        g_btnInterStart.setText("Arrêt");

                        // On grise les seekbars
                        setInterEnable(false);

                        // On poste le message
                        g_timeHandler.removeCallbacks(g_tTimerTask);
                        g_timeHandler.postDelayed(g_tTimerTask, 0);

                        switch(g_iCurrentTab){
                            case 0:
                                setGeneralEnable(false);
                                break;
                            case 1:
                                setInterEnable(false);
                                break;
                            default:
                                setLagEnable(false);
                                break;
                        }
                        break;
                    case LPT_INTER_STATE_INACTIVE:
                        g_bInterActive = false;
                        g_btnInterStart.setText("Démarrage");

                        // On stoppe le timer
                        g_timeHandler.removeCallbacks(g_tTimerTask);

                        // On efface
                        g_interStat.setTitle("");

                        // On dégrise les seekbars
                        setInterEnable(true);

                        // On réinitialise le compteur de temps
                        g_iInterTimeCounter = 0;

                        switch(g_iCurrentTab){
                            case 0:
                                setGeneralEnable(true);
                                break;
                            case 1:
                                setInterEnable(true);
                                break;
                            default:
                                setLagEnable(true);
                                break;
                        }
                        break;
                    case LPT_INTER_STATE_WAIT_NEXT:
                        g_bInterActive = true;
                        g_btnInterStart.setText("Arrêt");

                        // On grise les seekbars
                        setInterEnable(false);

                        // SI c'est actif, on sélectionne l'onglet "intervallomètre"
                        getActionBar().setSelectedNavigationItem(1);
                        g_viewPager.setCurrentItem(1);

                        // On lance le timer (directement avec un intervalle de 1000ms)
                        g_timeHandler.removeCallbacks(g_tTimerTask);
                        g_timeHandler.postDelayed(g_tTimerTask, 0);

                        switch(g_iCurrentTab){
                            case 0:
                                setGeneralEnable(false);
                                break;
                            case 1:
                                setInterEnable(false);
                                break;
                            default:
                                setLagEnable(false);
                                break;
                        }
                        break;
                    case LPT_INTER_STATE_WAIT_START:
                        g_bInterActive = true;
                        g_btnInterStart.setText("Arrêt");

                        // On grise les seekbars
                        setInterEnable(false);

                        // SI c'est actif, on sélectionne l'onglet "intervallomètre"
                        getActionBar().setSelectedNavigationItem(1);
                        g_viewPager.setCurrentItem(1);

                        // On lance le timer (directement avec un intervalle de 1000ms)
                        g_timeHandler.removeCallbacks(g_tTimerTask);
                        g_timeHandler.postDelayed(g_tTimerTask, 0);

                        switch(g_iCurrentTab){
                            case 0:
                                setGeneralEnable(false);
                                break;
                            case 1:
                                setInterEnable(false);
                                break;
                            default:
                                setLagEnable(false);
                                break;
                        }
                        break;
                }
                break;
            case LPT_GET_INTER_CURR_NB:
                g_iInterCurrPose = l_iTempValue;
                break;
            case LPT_GET_CUR_SENSITIVITY:
                g_seekSensitivity.setProgress(l_iTempValue);
                break;
            case LPT_GET_PREFOCUS:
                g_checkPrefocus.setChecked(l_iTempValue==1);
                break;
            case LPT_GET_BATT_LVL:
                g_iBattLvl = g_bIsLpt1?l_iTempValue/2:l_iTempValue;
                switch(g_iBattLvl){
                    case 1:
                        g_lvlBattIcon.setIcon(g_bCharging ? R.drawable.batt1ch : R.drawable.batt1);
                        break;
                    case 2:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt2ch:R.drawable.batt2);
                        break;
                    case 3:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt3ch:R.drawable.batt3);
                        break;
                    case 4:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt4ch:R.drawable.batt4);
                        break;
                    default:
                        g_lvlBattIcon.setIcon(g_bCharging?R.drawable.batt0ch:R.drawable.batt0);
                        break;
                }
                break;
            case LPT_GET_STRIKE_NB:
                break;
            case LPT_GET_STRIKE_VAL:
                break;
        }
    }

    // Afficher un texte en sous-titre...
    private void setStatus(String subtitle) {
        final ActionBar actionBar = getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subtitle);
    }

    // Griser/dégriser l'IHM
    private void setGeneralEnable(boolean enabled){
        g_seekLedLum.setEnabled(g_bIsLpt1?false:enabled);
        g_seekSensitivity.setEnabled(enabled);
        g_seekInhibit.setEnabled(enabled);
        g_checkPrefocus.setEnabled(enabled);
        g_checkExtSensor.setEnabled(enabled);
        g_seekExtDelay.setEnabled(g_bExternalSensor?enabled:false);
    }

    private void setInterEnable(boolean enabled){
        g_seekInterStart.setEnabled(enabled);
        g_seekInterShutter.setEnabled(enabled);
        g_seekInterInterval.setEnabled(enabled);
        g_seekInterNumber.setEnabled(enabled);
    }

    private void setLagEnable(boolean enabled){
        g_btnLag.setEnabled(enabled);
        g_check0.setEnabled(enabled);
        g_check1.setEnabled(enabled);
        g_check2.setEnabled(enabled);
        g_check3.setEnabled(enabled);
        g_check4.setEnabled(enabled);
        g_check5.setEnabled(enabled);
        g_check6.setEnabled(enabled);
        g_check7.setEnabled(enabled);
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
                g_btIcon.setIcon(R.drawable.bt_icon_c);
                setStatus("Connecté");
                g_bConnected = true;

                // Commandes de status
                g_btCom.sendCommand(LPT_GET_FW_VERSION, 0);
                g_btCom.sendCommand(LPT_GET_BATT_LVL, 0);
                g_btCom.sendCommand(LPT_GET_CHARGING, 0);

                switch(g_viewPager.getCurrentItem())
                {
                    case 0:
                        g_btCom.sendCommand(LPT_GET_PWM_LED, 0);
                        g_btCom.sendCommand(LPT_GET_CUR_SENSITIVITY, 0);
                        g_btCom.sendCommand(LPT_GET_INHIBITION, 0);
                        g_btCom.sendCommand(LPT_GET_PREFOCUS, 0);
                        g_btCom.sendCommand(LPT_GET_SENSOR, 0);
                        g_btCom.sendCommand(LPT_GET_LAG, 0);
                        break;
                    case 1:
                        g_btCom.sendCommand(LPT_GET_INTER_CURR_STAT, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_DELAY, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_SHUTTER, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_INTERVAL, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_NB_POSES, 0);
                        break;
                    default:
                        Log.d("MESSAGE", "Onglet Lag");
                        break;
                }
                Toast.makeText(getApplicationContext(), "Connecté au LPT avec succès!",
                        Toast.LENGTH_SHORT).show();
                Log.d("handlemessage", "Connected");
            } else if(co == 2){
                // On change l'icone du bouton
                g_btIcon.setIcon(R.drawable.bt_icon_n);

                // On stoppe les éventuelles callbacks timer
                g_timeHandler.removeCallbacks(g_tTimerTask);

                g_bConnected = false;
                Toast.makeText(getApplicationContext(), "Bluetooth déconnecté!",
                        Toast.LENGTH_SHORT).show();
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
        g_viewPager = (NonSwipeableViewPager) findViewById(R.id.pager);
        g_viewPager.setAdapter(g_pagerAdapter);
        g_viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // Ajout des onglets
        actionBar.addTab(actionBar.newTab().setText("GENERAL").setTabListener(this));
        actionBar.addTab(actionBar.newTab().setText("INTERVALLOMETRE").setTabListener(this));
        actionBar.addTab(actionBar.newTab().setText("LAG").setTabListener(this));

        // Sous-titre
        if(!g_bConnected)setStatus("Non connecté");
        else {
            // Sous-titre
            setStatus("Connecté");
        }

        // Vérification si on a le BT
        if(BluetoothAdapter.getDefaultAdapter() == null){
            Toast.makeText(getApplicationContext(), "Votre appareil ne dispose pas de Bluetooth!",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if(g_bConnected)g_btCom.close();
        finish(); // finish activity
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        g_menu = menu;

        // Création des éléments
        g_btIcon = g_menu.findItem(R.id.connectBtn);
        g_lvlBattIcon = g_menu.findItem(R.id.battIcon);
        g_firmTxt = g_menu.findItem(R.id.firmTxt);
        g_interStat = g_menu.findItem(R.id.interStat);

        if(g_bConnected){
            // Rafraîchissement du menu
            g_btCom.sendCommand(LPT_GET_FW_VERSION, 0);
            g_btCom.sendCommand(LPT_GET_BATT_LVL, 0);
            g_btCom.sendCommand(LPT_GET_CHARGING, 0);
            g_btIcon.setIcon(R.drawable.bt_icon_c);

        }
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
        g_iCurrentTab = tab.getPosition();
        switch (tab.getPosition()) {
            case 0:
                if(g_bInterActive == false){
                    if (g_bConnected) {
                        g_btCom.sendCommand(LPT_GET_PWM_LED, 0);
                        g_btCom.sendCommand(LPT_GET_CUR_SENSITIVITY, 0);
                        g_btCom.sendCommand(LPT_GET_INHIBITION, 0);
                        g_btCom.sendCommand(LPT_GET_PREFOCUS, 0);
                        g_btCom.sendCommand(LPT_GET_SENSOR, 0);
                        g_btCom.sendCommand(LPT_GET_LAG, 0);
                    }
                    setGeneralEnable(true);
                } else {
                    setGeneralEnable(false);
                }
                break;
            case 1:
                if(g_bInterActive == false) {
                    if (g_bConnected) {
                        g_btCom.sendCommand(LPT_GET_INTER_CURR_STAT, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_DELAY, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_SHUTTER, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_INTERVAL, 0);
                        g_btCom.sendCommand(LPT_GET_INTER_NB_POSES, 0);
                    }
                }
                break;
            default:
                if(g_bInterActive == false){
                    setLagEnable(true);
                } else {
                    setLagEnable(false);
                }
                break;
        }
    }

    private Runnable g_tTimerTask = new Runnable() {
        @Override
        public void run() {
            // Si changement d'état, on réinitialise le compteur
            if(g_iCurrentInterStatus != g_iPreviousInterStatus) g_iInterTimeCounter = 0;

            switch(g_iCurrentInterStatus){
                case LPT_INTER_STATE_ACTIVE:
                    String l_actStr = "P " + g_iInterCurrPose + "/" + (g_iInterNbPoses==0?"INF":g_iInterNbPoses) + "\n" + g_iInterTimeCounter + "s";
                    g_interStat.setTitle(l_actStr);
                    break;
                case LPT_INTER_STATE_WAIT_START:
                    String l_stStr = "START\n" + g_iInterTimeCounter + "s";
                    g_interStat.setTitle(l_stStr);
                    break;
                case LPT_INTER_STATE_WAIT_NEXT:
                    String l_nxtStr = "ATT P" + (g_iInterCurrPose+1) + "\n" + g_iInterTimeCounter + "s";
                    g_interStat.setTitle(l_nxtStr);
                    break;
            }

            // On incrémente le temps
            g_iInterTimeCounter++;

            // On met l'état
            g_iPreviousInterStatus = g_iCurrentInterStatus;

            // On poste le prochain dans 1000ms
            g_timeHandler.postDelayed(this, 1000);
        }
    };

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
            g_btCom.close();
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

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View l_view = inflater.inflate(R.layout.fragment_general, container, false);

            // Création des éléments
            g_seekLedLum = (SeekBar)l_view.findViewById(R.id.seekLedLuminosity);
            g_seekSensitivity = (SeekBar)l_view.findViewById(R.id.seekSensitivity);
            g_seekInhibit = (SeekBar)l_view.findViewById(R.id.seekInhibition);
            g_checkPrefocus = (CheckBox)l_view.findViewById(R.id.checkPrefocus);
            g_checkExtSensor = (CheckBox)l_view.findViewById(R.id.checkExtSensor);
            g_seekExtDelay = (SeekBar)l_view.findViewById(R.id.seekExtDelay);

            // Luminosité LEDs
            g_seekLedLum.setMax(100);
            g_seekLedLum.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.valLedLum);
                    String val = Integer.toString(progress>4?progress:4) + "%";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_PWM_LED, seekBar.getProgress()>4?seekBar.getProgress():4);
                }
            });

            // Sensibilité
            g_seekSensitivity.setMax(4);
            g_seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView)l_view.findViewById(R.id.valSensitivity);
                    text.setText(Integer.toString(progress));
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_CUR_SENSITIVITY, seekBar.getProgress());
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            // Inhibition
            g_seekInhibit.setMax(100);
            g_seekInhibit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.valInhibit);
                    String val = Integer.toString(progress * 100) + "ms";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_INHIBITION, seekBar.getProgress()*100);
                }
            });

            // Préfocus
            g_checkPrefocus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (g_bConnected) {
                        g_btCom.sendCommand(LPT_SET_PREFOCUS, g_checkPrefocus.isChecked()?1:0);
                    }
                }
            });

            // Capteur externe
            g_checkExtSensor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    g_seekExtDelay.setEnabled(g_checkExtSensor.isChecked());
                    if(g_bConnected){
                        g_btCom.sendCommand(LPT_SET_SENSOR, g_checkExtSensor.isChecked()?1:0);
                    }
                }
            });

            // Retard déclenchement capteur externe
            g_seekExtDelay.setMax(100);
            g_seekExtDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.valExtDelay);
                    String val = Integer.toString(progress) + "ms";
                    text.setText(val);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_LAG, seekBar.getProgress());
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

            // Création des éléments graphiques
            g_seekInterStart = (SeekBar)l_view.findViewById(R.id.seekInterStart);
            g_seekInterShutter = (SeekBar)l_view.findViewById(R.id.seekInterShutter);
            g_seekInterInterval = (SeekBar)l_view.findViewById(R.id.seekInterInterval);
            g_seekInterNumber = (SeekBar)l_view.findViewById(R.id.seekInterNumber);
            g_btnInterStart = (Button)l_view.findViewById(R.id.btnInterStart);

            // Seekbars de réglages
            g_seekInterStart.setMax(60);
            g_seekInterStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterStart);
                    String val = Integer.toString(progress>0?progress:1) + " s";
                    text.setText(val);
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_INTER_DELAY, progress>0?progress:1);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            g_seekInterShutter.setMax(60);
            g_seekInterShutter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterShutter);
                    String val = Integer.toString(progress>0?progress:1) + " s";
                    text.setText(val);
                    if (g_bConnected) g_btCom.sendCommand(LPT_SET_INTER_SHUTTER, progress>0?progress:1);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            g_seekInterInterval.setMax(300);
            g_seekInterInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterInterval);
                    String val = Integer.toString(progress>0?progress:1) + " s";
                    text.setText(val);
                    if(g_bConnected)g_btCom.sendCommand(LPT_SET_INTER_INTERVAL, progress>0?progress:1);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            g_seekInterNumber.setMax(99);
            g_seekInterNumber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    TextView text = (TextView) l_view.findViewById(R.id.txtInterNumber);
                    text.setText(progress > 0 ? Integer.toString(progress) : "INF");
                    if(g_bConnected) g_btCom.sendCommand(LPT_SET_INTER_NB_POSES, progress);
                    g_iInterNbPoses = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            g_btnInterStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(g_bConnected){
                        if(g_btnInterStart.getText() == "Arrêt"){
                            g_btCom.sendCommand(LPT_SET_INTER_STOP, 0);
                        }
                        else{
                            g_btCom.sendCommand(LPT_SET_INTER_START, 0);
                        }
                    }


                }
            });

            return l_view;
        }
    }

    public static class LagFragment extends Fragment {
        int g_iLagVal = 0;


        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View l_view = inflater.inflate(R.layout.fragment_lag, container, false);

            // Création des éléments graphiques
            g_btnLag = (Button)l_view.findViewById(R.id.btnStartLag);
            g_txtRes = (TextView)l_view.findViewById(R.id.txtResult);
            g_check0 = (CheckBox)l_view.findViewById(R.id.checkBox0);
            g_check1 = (CheckBox)l_view.findViewById(R.id.checkBox1);
            g_check2 = (CheckBox)l_view.findViewById(R.id.checkBox2);
            g_check3 = (CheckBox)l_view.findViewById(R.id.checkBox3);
            g_check4 = (CheckBox)l_view.findViewById(R.id.checkBox4);
            g_check5 = (CheckBox)l_view.findViewById(R.id.checkBox5);
            g_check6 = (CheckBox)l_view.findViewById(R.id.checkBox6);
            g_check7 = (CheckBox)l_view.findViewById(R.id.checkBox7);

            // Bouton lancement lag
            g_btnLag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (g_bConnected) g_btCom.sendCommand((byte) 6, 0);
                }
            });

            // Checkboxes
            g_check0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?1:-1;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?2:-2;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?4:-4;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?8:-8;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?16:-16;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?32:-32;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

            g_check6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    g_iLagVal += isChecked?64:-64;
                    g_txtRes.setText(Integer.toString(g_iLagVal));
                }
            });

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
