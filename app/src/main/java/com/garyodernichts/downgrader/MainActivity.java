package com.garyodernichts.downgrader;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    public static final int CHOOSE_APK_RESULT = 1;
    private static final int RC_EXTERNAL_STORAGE = 69;

    private TextView tvApkName;
    private Button btDowngrade;

    private Uri apkUri;
    private String apkPath;
    private boolean apkSet;

    static {
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.setTimeout(10);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getExtStoragePermission();

        tvApkName = findViewById(R.id.tvApkName);

        final Button btChooseApk = findViewById(R.id.btChooseApk);
        btChooseApk.setOnClickListener(view -> {
            getExtStoragePermission();
            if (!hasExtStoragePermission())
                return;

            Intent chooseApk = new Intent(Intent.ACTION_GET_CONTENT);
            chooseApk.setType("application/vnd.android.package-archive");
            chooseApk = Intent.createChooser(chooseApk, "Choose an APK");
            startActivityForResult(chooseApk, CHOOSE_APK_RESULT);
        });

        btDowngrade = findViewById(R.id.btDowngrade);
        btDowngrade.setOnClickListener(view -> {
            getExtStoragePermission();
            if (!hasExtStoragePermission())
                return;

            if (apkSet) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(apkUri);
                    File src = new File(apkPath);
                        try (OutputStream out = new FileOutputStream(getCacheDir() + "/" + src.getName())) {
                            byte[] buf = new byte[1024];
                            int len;
                            assert inputStream != null;
                            while ((len = inputStream.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Shell.su("pm install -r -d " + getCacheDir() + "/" + new File(apkPath).getName()).submit(result -> {
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

        if (!apkSet) {
            btDowngrade.setEnabled(false);
            clearCache();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data != null && data.getData() != null) {
            switch (requestCode) {
                case CHOOSE_APK_RESULT:
                    if (resultCode == -1) {
                        apkSet = true;
                        apkUri = data.getData();
                        apkPath = apkUri.getPath();

                        tvApkName.setText(new File(apkPath).getName());
                        btDowngrade.setEnabled(true);
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
}
