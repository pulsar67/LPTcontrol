package com.p67world.lptcontrol;


import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener {

    // Adaptateur permet de récupérer le fragment
    AppSectionsPagerAdapter g_pagerAdapter;

    // Permet de lier l'onglet au fragment concerner
    ViewPager g_viewPager;

    // Adapter Bluetooth
    private BluetoothAdapter g_btAdapter = BluetoothAdapter.getDefaultAdapter();

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
        if(g_btAdapter == null){
            Toast.makeText(getApplicationContext(), "Votre appareil ne dispose pas de Bluetooth!",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
        // On active le BT si nécessaire
        if(!g_btAdapter.isEnabled()) {
            g_btAdapter.enable();
            Toast.makeText(getApplicationContext(), "Le Bluetooth n'etait pas actif et a ete active",
                    Toast.LENGTH_SHORT).show();
        }

        // On crée l'activité de scan
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, 1);
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
