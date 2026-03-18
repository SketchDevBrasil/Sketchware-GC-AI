package pro.sketchware.activities.main.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.besome.sketch.lib.base.BasePermissionAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import a.a.a.DB;
import a.a.a.GB;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.tyron.backup.SingleCopyTask;
import pro.sketchware.R;
import pro.sketchware.activities.about.AboutActivity;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.MainBinding;
import pro.sketchware.lib.base.BottomSheetDialogView;
import pro.sketchware.utility.DataResetter;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class MainActivity extends BasePermissionAppCompatActivity {
    private static final String PROJECTS_FRAGMENT_TAG = "projects_fragment";
    private ActionBarDrawerToggle drawerToggle;
    private DB u;
    private Snackbar storageAccessDenied;
    private MainBinding binding;
    private final OnBackPressedCallback closeDrawer = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            setEnabled(false);
            binding.drawerLayout.closeDrawers();
        }
    };
    private ProjectsFragment projectsFragment;
    private Fragment activeFragment;
    @IdRes
    private int currentNavItemId = R.id.item_store;


    @Override
    // onRequestPermissionsResult but for Storage access only, and only when granted
    public void g(int i) {
        if (i == 9501) {
            allFilesAccessCheck();

            if (activeFragment instanceof ProjectsFragment) {
                projectsFragment.refreshProjectsList();
            }
        }
    }

    @Override
    public void h(int i) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
        startActivityForResult(intent, i);
    }

    @Override
    public void l() {
    }

    @Override
    public void m() {
    }

    public void n() {
        if (activeFragment instanceof ProjectsFragment) {
            projectsFragment.refreshProjectsList();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 105:
                    DataResetter.a(this, data.getBooleanExtra("onlyConfig", true));
                    break;

                case 111:
                    invalidateOptionsMenu();
                    break;

                case 113:
                    if (data != null && data.getBooleanExtra("not_show_popup_anymore", false)) {
                        u.a("U1I2", (Object) false);
                    }
                    break;

                case 212:
                    if (!(data.getStringExtra("save_as_new_id") == null ? "" : data.getStringExtra("save_as_new_id")).isEmpty() && isStoragePermissionGranted()) {
                        if (activeFragment instanceof ProjectsFragment) {
                            projectsFragment.refreshProjectsList();
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        enableEdgeToEdgeNoContrast();

        binding = MainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.statusBarOverlapper.setMinimumHeight(UI.getStatusBarHeight(this));
        UI.addSystemWindowInsetToPadding(binding.appbar, true, false, true, false);

        u = new DB(getApplicationContext(), "U1");
        int u1I0 = u.a("U1I0", -1);
        long u1I1 = u.e("U1I1");
        if (u1I1 <= 0) {
            u.a("U1I1", System.currentTimeMillis());
        }
        if (System.currentTimeMillis() - u1I1 > /* (a day) */ 1000 * 60 * 60 * 24) {
            u.a("U1I0", Integer.valueOf(u1I0 + 1));
        }

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(null);

        drawerToggle = new ActionBarDrawerToggle(this, binding.drawerLayout, R.string.app_name, R.string.app_name);
        binding.drawerLayout.addDrawerListener(drawerToggle);
        binding.drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                closeDrawer.setEnabled(true);
                getOnBackPressedDispatcher().addCallback(closeDrawer);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        boolean hasStorageAccess = isStoragePermissionGranted();
        if (!hasStorageAccess) {
            showNoticeNeedStorageAccess();
        }
        if (hasStorageAccess) {
            allFilesAccessCheck();
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data != null) {
                new SingleCopyTask(this, new SingleCopyTask.CallBackTask() {
                    @Override
                    public void onCopyPreExecute() {
                    }

                    @Override
                    public void onCopyProgressUpdate(int progress) {
                    }

                    @Override
                    public void onCopyPostExecute(@NonNull String path, boolean wasSuccessful, @NonNull String reason) {
                        if (wasSuccessful) {
                            BackupRestoreManager manager = new BackupRestoreManager(MainActivity.this, projectsFragment);

                            if (BackupFactory.zipContainsFile(path, "local_libs")) {
                                new MaterialAlertDialogBuilder(MainActivity.this)
                                        .setTitle("Warning")
                                        .setMessage(BackupRestoreManager.getRestoreIntegratedLocalLibrariesMessage(false, -1, -1, null))
                                        .setPositiveButton("Copy", (dialog, which) -> manager.doRestore(path, true))
                                        .setNegativeButton("Don't copy", (dialog, which) -> manager.doRestore(path, false))
                                        .setNeutralButton(R.string.common_word_cancel, null)
                                        .show();
                            } else {
                                manager.doRestore(path, true);
                            }

                            // Clear intent so it doesn't duplicate
                            getIntent().setData(null);
                        } else {
                            SketchwareUtil.toastError("Failed to copy backup file to temporary location: " + reason, Toast.LENGTH_LONG);
                        }
                    }
                }).copyFile(data);
            }
        } else if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_CRITICAL_UPDATE_REMINDER)) {
            BottomSheetDialogView bottomSheetDialog = getBottomSheetDialogView();
            bottomSheetDialog.getPositiveButton().setEnabled(false);

            CountDownTimer countDownTimer = new CountDownTimer(10000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    bottomSheetDialog.setPositiveButtonText(millisUntilFinished / 1000 + "");
                }

                @Override
                public void onFinish() {
                    bottomSheetDialog.setPositiveButtonText("View changes");
                    bottomSheetDialog.getPositiveButton().setEnabled(true);
                }
            };
            countDownTimer.start();

            if (!isFinishing()) bottomSheetDialog.show();
        }

        // Custom link bar — apply system nav bar insets from root so CoordinatorLayout doesn't swallow them
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            binding.bottomLinkBar.setPadding(0, 0, 0, bottomInset);
            // Keep FAB above the bar (56dp bar height + inset + 8dp gap)
            int fabBottomMargin = bottomInset + (int)(56 * getResources().getDisplayMetrics().density) + (int)(8 * getResources().getDisplayMetrics().density);
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams fabParams =
                (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) binding.createNewProject.getLayoutParams();
            fabParams.bottomMargin = fabBottomMargin;
            binding.createNewProject.setLayoutParams(fabParams);
            return insets;
        });

        binding.btnLinkStore.setOnClickListener(v -> openUrl("https://sketch-dev-brasil.web.app/home"));
        binding.btnLinkTelegram.setOnClickListener(v -> openUrl("https://t.me/sketchdevbrasil"));
        binding.btnLinkYoutube.setOnClickListener(v -> openUrl("https://youtube.com/@sketchdevbrasil"));
        binding.btnLinkUpdates.setOnClickListener(v -> openUrl("https://sketch-dev-brasil.web.app/sdbcodflow"));

        if (savedInstanceState != null) {
            projectsFragment = (ProjectsFragment) getSupportFragmentManager().findFragmentByTag(PROJECTS_FRAGMENT_TAG);
            currentNavItemId = savedInstanceState.getInt("selected_tab_id");
            navigateToProjectsFragment();
            return;
        }

        navigateToProjectsFragment();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private Fragment getFragmentForNavId(int navItemId) {
        return projectsFragment;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_tab_id", currentNavItemId);
    }

    private void navigateToProjectsFragment() {
        if (projectsFragment == null) {
            projectsFragment = new ProjectsFragment();
        }

        boolean shouldShow = true;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        binding.createNewProject.show();
        if (activeFragment != null) transaction.hide(activeFragment);
        if (fm.findFragmentByTag(PROJECTS_FRAGMENT_TAG) == null) {
            shouldShow = false;
            transaction.add(binding.container.getId(), projectsFragment, PROJECTS_FRAGMENT_TAG);
        }
        if (shouldShow) transaction.show(projectsFragment);
        transaction.commit();

        activeFragment = projectsFragment;
        currentNavItemId = R.id.item_store;
    }


    @NonNull
    private BottomSheetDialogView getBottomSheetDialogView() {
        BottomSheetDialogView bottomSheetDialog = new BottomSheetDialogView(this);
        bottomSheetDialog.setTitle("Sketchware Pro SDBCodFlow v8.7.7");
        bottomSheetDialog.setDescription("""
                Bem-vindo ao Sketchware Pro SDBCodFlow! Esta versão traz melhorias críticas de estabilidade \
                e a integração avançada da IA Agente SDB.
                
                Agora você pode usar IA para injetar lógica diretamente no seu projeto and ver previews do código antes de aplicar.""");
        bottomSheetDialog.setImage(R.drawable.ic_sdbcodflow_sparkle_img);

        bottomSheetDialog.setPositiveButton("View changes", (dialog, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_CRITICAL_UPDATE_REMINDER, true);
            Intent launcher = new Intent(this, AboutActivity.class);
            launcher.putExtra("select", "changelog");
            startActivity(launcher);
        });
        bottomSheetDialog.setCancelable(false);
        return bottomSheetDialog;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onResume() {
        super.onResume();
        /* Check if the device is running low on storage space */
        long freeMegabytes = GB.c();
        if (freeMegabytes < 100 && freeMegabytes > 0) {
            showNoticeNotEnoughFreeStorageSpace();
        }
        if (isStoragePermissionGranted() && storageAccessDenied != null && storageAccessDenied.isShown()) {
            storageAccessDenied.dismiss();
        }
    }

    private void allFilesAccessCheck() {
        if (Build.VERSION.SDK_INT > 29) {
            File optOutFile = new File(getFilesDir(), ".skip_all_files_access_notice");
            boolean granted = Environment.isExternalStorageManager();

            if (!optOutFile.exists() && !granted) {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
                dialog.setIcon(R.drawable.ic_expire_48dp);
                dialog.setTitle("Android 11 storage access");
                dialog.setMessage("Starting with Android 11, Sketchware Pro needs a new permission to avoid " + "taking ages to build projects. Don't worry, we can't do more to storage than " + "with current granted permissions.");
                dialog.setPositiveButton(Helper.getResString(R.string.common_word_settings), (v, which) -> {
                    FileUtil.requestAllFilesAccessPermission(this);
                    v.dismiss();
                });
                dialog.setNegativeButton("Skip", null);
                dialog.setNeutralButton("Don't show anymore", (v, which) -> {
                    try {
                        if (!optOutFile.createNewFile())
                            throw new IOException("Failed to create file " + optOutFile);
                    } catch (IOException e) {
                        Log.e("MainActivity", "Error while trying to create " + "\"Don't show Android 11 hint\" dialog file: " + e.getMessage(), e);
                    }
                    v.dismiss();
                });
                dialog.show();
            }
        }
    }

    private void showNoticeNeedStorageAccess() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_message_permission_title_storage));
        dialog.setIcon(R.drawable.color_about_96);
        dialog.setMessage(Helper.getResString(R.string.common_message_permission_need_load_project));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), (v, which) -> {
            v.dismiss();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 9501);
        });
        dialog.show();
    }

    private void showNoticeNotEnoughFreeStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_message_insufficient_storage_space_title));
        dialog.setIcon(R.drawable.high_priority_96_red);
        dialog.setMessage(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    public void s() {
        if (storageAccessDenied == null || !storageAccessDenied.isShown()) {
            storageAccessDenied = Snackbar.make(binding.layoutCoordinator, Helper.getResString(R.string.common_message_permission_denied), Snackbar.LENGTH_INDEFINITE);
            storageAccessDenied.setAction(Helper.getResString(R.string.common_word_settings), v -> {
                storageAccessDenied.dismiss();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 9501);
            });
            storageAccessDenied.setActionTextColor(Color.YELLOW);
            storageAccessDenied.show();
        }
    }

}
