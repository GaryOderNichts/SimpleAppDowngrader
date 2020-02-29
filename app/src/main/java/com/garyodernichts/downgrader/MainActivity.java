package com.garyodernichts.downgrader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.topjohnwu.superuser.BuildConfig;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    public static final int CHOOSE_APK_RESULT = 1;
    private static final int RC_EXTERNAL_STORAGE = 69;

    private static final String APK_SET_KEY = "apkset";
    private static final String CACHE_NAME_KEY = "cachename";

    private Button btDowngrade;

    private boolean apkSet;
    private String cacheName;

    static {
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.setTimeout(300);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!hasExtStoragePermission())
            getExtStoragePermission();

        final Button btChooseApk = findViewById(R.id.btChooseApk);
        btChooseApk.setOnClickListener(view -> {
            getExtStoragePermission();
            if (!hasExtStoragePermission())
                return;

            apkSet = false;
            Intent chooseApk = new Intent(Intent.ACTION_GET_CONTENT);
            chooseApk.setType("application/vnd.android.package-archive");
            chooseApk = Intent.createChooser(chooseApk, "Choose an APK");
            startActivityForResult(chooseApk, CHOOSE_APK_RESULT);
        });

        final ProgressBar pbDowngrade = findViewById(R.id.pbDowngrade);
        btDowngrade = findViewById(R.id.btDowngrade);
        btDowngrade.setOnClickListener(view -> {
            getExtStoragePermission();
            if (!hasExtStoragePermission())
                return;

            if (apkSet) {
                pbDowngrade.setVisibility(View.VISIBLE);

                Shell.su("pm install -r -d " + getCacheDir() + "/" + cacheName).submit(result -> {
                    pbDowngrade.setVisibility(View.INVISIBLE);

                    if (result.isSuccess()) {
                        Snackbar.make(findViewById(android.R.id.content), "Downgrade successful!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage("Error downgrading app: " + result.getOut().toString())
                                .setCancelable(false)
                                .setPositiveButton("OK", (dialog, id) -> {
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                });
            }
        });

        if (savedInstanceState != null) {
            apkSet = savedInstanceState.getBoolean(APK_SET_KEY, false);

            if (apkSet) {
                cacheName = savedInstanceState.getString(CACHE_NAME_KEY);

                final LinearLayout chosenApkLayout = findViewById(R.id.newApkInfo);
                chosenApkLayout.setVisibility(View.VISIBLE);
                final LinearLayout installedApkLayout = findViewById(R.id.currentlyInstalledInfo);
                installedApkLayout.setVisibility(View.VISIBLE);

                updateInfoTextViews(getCacheDir() + "/" + cacheName);
            }
        }

        if (!apkSet) {
            btDowngrade.setEnabled(false);
            clearCache();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data != null) {
            switch (requestCode) {
                case CHOOSE_APK_RESULT:
                    if (resultCode == -1) {
                        handleFileBrowseReturn(data);
                    }
                    break;
                case AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE:
                    // reserved
                    break;
            }
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(APK_SET_KEY, apkSet);
        outState.putString(CACHE_NAME_KEY, cacheName);
    }

    public void getExtStoragePermission() {
        if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            EasyPermissions.requestPermissions(this, getString(R.string.rationale_external_storage), RC_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public boolean hasExtStoragePermission() {
        return EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public void clearCache() {
        File dir = getCacheDir();
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String child : children) {
                new File(dir, child).delete();
            }
        }
    }

    public void copyToCache(Uri apkUri, String name) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(apkUri);
        try (OutputStream out = new FileOutputStream(getCacheDir() + "/" + name)) {
            byte[] buf = new byte[1024];
            int len;
            assert inputStream != null;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    public void handleFileBrowseReturn(Intent data) {
        if (data.getData() != null) {
            apkSet = true;

            final LinearLayout chosenApkLayout = findViewById(R.id.newApkInfo);
            chosenApkLayout.setVisibility(View.INVISIBLE);

            final LinearLayout installedApkLayout = findViewById(R.id.currentlyInstalledInfo);
            installedApkLayout.setVisibility(View.INVISIBLE);

            final ProgressBar pbCopy = findViewById(R.id.pbCopy);
            pbCopy.setVisibility(View.VISIBLE);

            String name = new File(data.getData().getPath()).getName();
            cacheName = name;
            try {
                copyToCache(data.getData(), name);
            } catch (IOException e) {
                e.printStackTrace();
            }

            pbCopy.setVisibility(View.INVISIBLE);
            chosenApkLayout.setVisibility(View.VISIBLE);
            installedApkLayout.setVisibility(View.VISIBLE);

            updateInfoTextViews(getCacheDir() + "/" + name);

            btDowngrade.setEnabled(true);
        }
    }

    public void updateInfoTextViews(String path) {
        final PackageManager pm = getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(path, 0);

        final TextView tvChosenPackageId = findViewById(R.id.tvChosenPackageId);
        tvChosenPackageId.setText(info.packageName);
        final TextView tvChosenVersion = findViewById(R.id.tvChosenVersion);
        tvChosenVersion.setText(String.format(Locale.getDefault(), "%s (%d)", info.versionName, info.versionCode));

        PackageInfo installedInfo = null;
        try {
            installedInfo = pm.getPackageInfo(info.packageName, 0);
        } catch (PackageManager.NameNotFoundException ignored) { }

        final TextView tvInstalledPackageId = findViewById(R.id.tvInstalledPackageId);
        tvInstalledPackageId.setText(installedInfo != null ? installedInfo.packageName : "");
        final TextView tvInstalledVersion = findViewById(R.id.tvInstalledVersion);
        tvInstalledVersion.setText(installedInfo != null ? String.format(Locale.getDefault(), "%s (%d)", installedInfo.versionName, installedInfo.versionCode) : "");
    }
}
