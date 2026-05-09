package mod.hey.studios.project.github;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.DialogGithubImportBinding;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.Network;
import pro.sketchware.utility.SketchwareUtil;

public class GitHubImportManager {
    private final Activity activity;
    private final ProjectsFragment projectsFragment;

    public GitHubImportManager(Activity activity, ProjectsFragment projectsFragment) {
        this.activity = activity;
        this.projectsFragment = projectsFragment;
    }

    public void showImportDialog() {
        DialogGithubImportBinding binding = DialogGithubImportBinding.inflate(LayoutInflater.from(activity));

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setTitle("Import from GitHub");
        dialog.setIcon(R.drawable.ic_mtrl_github);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton("Import", (d, which) -> {
            String url = binding.etUrl.getText().toString().trim();
            String[] parsed = GitHubApiClient.parseGitHubUrl(url);

            if (parsed == null) {
                SketchwareUtil.toastError("Invalid GitHub URL. Use: https://github.com/user/repo");
                return;
            }

            fetchAndShowFiles(parsed[0], parsed[1]);
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void fetchAndShowFiles(String owner, String repo) {
        ProgressMsgBoxBinding progressBinding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(activity));
        progressBinding.tvProgress.setText("Fetching repository contents...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Import from GitHub")
                .setCancelable(false)
                .setView(progressBinding.getRoot())
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                String token = GitHubTokenManager.getToken(activity);
                GitHubApiClient client = new GitHubApiClient(token);
                Network.SyncResponse response = client.listRepoContentsSync(owner, repo, null);

                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();

                    if (!response.isSuccessful()) {
                        if (response.code == 404 && !GitHubTokenManager.isLoggedIn(activity)) {
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle("Authentication required")
                                    .setMessage("This repository may be private or doesn't exist.\nWould you like to log in to GitHub?")
                                    .setPositiveButton("Login", (d, w) -> {
                                        new GitHubUploadManager(activity).showLoginDialog(
                                                () -> fetchAndShowFiles(owner, repo));
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            SketchwareUtil.toastError("Failed to access repository (HTTP " + response.code + ")");
                        }
                        return;
                    }

                    try {
                        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                        List<Map<String, Object>> contents = new Gson().fromJson(response.body, listType);

                        List<Map<String, Object>> swbFiles = new ArrayList<>();
                        for (Map<String, Object> file : contents) {
                            String name = (String) file.get("name");
                            if (name != null && name.endsWith("." + BackupFactory.EXTENSION)) {
                                swbFiles.add(file);
                            }
                        }

                        if (swbFiles.isEmpty()) {
                            SketchwareUtil.toastError("No .swb backup files found in this repository");
                            return;
                        }

                        if (swbFiles.size() == 1) {
                            String downloadUrl = (String) swbFiles.get(0).get("download_url");
                            String fileName = (String) swbFiles.get(0).get("name");
                            startDownloadAndRestore(downloadUrl, fileName);
                        } else {
                            showFileSelectionDialog(swbFiles);
                        }

                    } catch (Exception e) {
                        SketchwareUtil.toastError("Failed to parse response: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    SketchwareUtil.toastError("Connection failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showFileSelectionDialog(List<Map<String, Object>> swbFiles) {
        String[] fileNames = new String[swbFiles.size()];
        for (int i = 0; i < swbFiles.size(); i++) {
            fileNames[i] = (String) swbFiles.get(i).get("name");
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select backup file")
                .setItems(fileNames, (dialog, which) -> {
                    String downloadUrl = (String) swbFiles.get(which).get("download_url");
                    startDownloadAndRestore(downloadUrl, fileNames[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadAndRestore(String downloadUrl, String fileName) {
        String token = GitHubTokenManager.getToken(activity);
        new ImportAsyncTask(new WeakReference<>(activity), downloadUrl, fileName, token, projectsFragment).execute();
    }

    private static class ImportAsyncTask extends AsyncTask<Void, String, String> {
        private final WeakReference<Activity> activityRef;
        private final String downloadUrl;
        private final String fileName;
        private final String token;
        private final ProjectsFragment projectsFragment;
        private AlertDialog progressDialog;
        private boolean success = false;

        ImportAsyncTask(WeakReference<Activity> activityRef, String downloadUrl, String fileName,
                        String token, ProjectsFragment projectsFragment) {
            this.activityRef = activityRef;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
            this.token = token;
            this.projectsFragment = projectsFragment;
        }

        @Override
        protected void onPreExecute() {
            Activity act = activityRef.get();
            if (act == null) return;

            ProgressMsgBoxBinding binding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(act));
            binding.tvProgress.setText("Downloading...");
            progressDialog = new MaterialAlertDialogBuilder(act)
                    .setTitle("Importing from GitHub")
                    .setCancelable(false)
                    .setView(binding.getRoot())
                    .create();
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                // Step 1: Download file
                publishProgress("Downloading " + fileName + "...");
                GitHubApiClient client = new GitHubApiClient(token);
                byte[] fileBytes = client.downloadFileBytes(downloadUrl);

                if (fileBytes == null || fileBytes.length == 0) {
                    return "Failed to download file (empty response)";
                }

                // Step 2: Save to temp file
                publishProgress("Saving file...");
                File tempDir = new File(BackupFactory.getBackupDir(), "github_temp");
                tempDir.mkdirs();
                File tempFile = new File(tempDir, fileName);

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(fileBytes);
                }

                // Step 3: Restore project
                publishProgress("Restoring project...");
                String newScId = BackupFactory.getNewScId();
                BackupFactory factory = new BackupFactory(newScId);
                factory.setBackupLocalLibs(true);
                factory.restore(tempFile);

                if (!factory.isRestoreSuccess()) {
                    return "Failed to restore: " + factory.getError();
                }

                // Clean up temp file
                tempFile.delete();
                tempDir.delete();

                success = true;
                return "Project imported successfully";

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (progressDialog != null && progressDialog.isShowing()) {
                TextView tv = progressDialog.findViewById(R.id.tv_progress);
                if (tv != null) tv.setText(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (success) {
                SketchwareUtil.toast(result);
                if (projectsFragment != null) {
                    projectsFragment.refreshProjectsList();
                }
            } else {
                SketchwareUtil.toastError(result, Toast.LENGTH_LONG);
            }
        }
    }
}
