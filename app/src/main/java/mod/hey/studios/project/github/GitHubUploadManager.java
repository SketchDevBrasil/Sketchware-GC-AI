package mod.hey.studios.project.github;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Base64;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogGithubLoginBinding;
import pro.sketchware.databinding.DialogGithubUploadBinding;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.SketchwareUtil;

public class GitHubUploadManager {
    private final Activity activity;

    public GitHubUploadManager(Activity activity) {
        this.activity = activity;
    }

    public void upload(String scId, String appName) {
        if (!GitHubTokenManager.isLoggedIn(activity)) {
            showLoginDialog(() -> showUploadDialog(scId, appName));
        } else {
            showUploadDialog(scId, appName);
        }
    }

    public void showLoginDialog(Runnable onSuccess) {
        DialogGithubLoginBinding binding = DialogGithubLoginBinding.inflate(LayoutInflater.from(activity));

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setTitle("GitHub Login");
        dialog.setIcon(R.drawable.ic_mtrl_github);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton("Login", (d, which) -> {
            String token = binding.etToken.getText().toString().trim();
            if (token.isEmpty()) {
                SketchwareUtil.toastError("Token cannot be empty");
                return;
            }

            GitHubApiClient client = new GitHubApiClient(token);
            client.validateToken(response -> {
                if (response == null) {
                    SketchwareUtil.toastError("Failed to connect to GitHub");
                    return;
                }

                try {
                    Map<String, Object> user = new Gson().fromJson(response, Map.class);
                    if (user.containsKey("login")) {
                        String username = (String) user.get("login");
                        GitHubTokenManager.saveToken(activity, token);
                        GitHubTokenManager.saveUsername(activity, username);
                        SketchwareUtil.toast("Logged in as " + username);
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        SketchwareUtil.toastError("Invalid token");
                    }
                } catch (Exception e) {
                    SketchwareUtil.toastError("Invalid token: " + e.getMessage());
                }
            });
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void showUploadDialog(String scId, String appName) {
        DialogGithubUploadBinding binding = DialogGithubUploadBinding.inflate(LayoutInflater.from(activity));

        // Pre-fill with project info
        String repoName = appName.replaceAll("[^a-zA-Z0-9_\\-.]", "-").toLowerCase();
        binding.etRepoName.setText(repoName);
        binding.etDescription.setText("Sketchware project: " + appName);

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setTitle("Upload to GitHub");
        dialog.setIcon(R.drawable.ic_mtrl_github);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton("Upload", (d, which) -> {
            String name = binding.etRepoName.getText().toString().trim();
            String description = binding.etDescription.getText().toString().trim();
            boolean isPrivate = binding.switchPrivate.isChecked();

            if (name.isEmpty()) {
                SketchwareUtil.toastError("Repository name cannot be empty");
                return;
            }

            new UploadAsyncTask(
                    new WeakReference<>(activity),
                    scId, appName, name, description, isPrivate
            ).execute();
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private static class UploadAsyncTask extends AsyncTask<Void, String, String> {
        private final WeakReference<Activity> activityRef;
        private final String scId;
        private final String appName;
        private final String repoName;
        private final String description;
        private final boolean isPrivate;
        private AlertDialog progressDialog;
        private boolean success = false;

        UploadAsyncTask(WeakReference<Activity> activityRef, String scId, String appName,
                        String repoName, String description, boolean isPrivate) {
            this.activityRef = activityRef;
            this.scId = scId;
            this.appName = appName;
            this.repoName = repoName;
            this.description = description;
            this.isPrivate = isPrivate;
        }

        @Override
        protected void onPreExecute() {
            Activity act = activityRef.get();
            if (act == null) return;

            ProgressMsgBoxBinding binding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(act));
            binding.tvProgress.setText("Creating backup...");
            progressDialog = new MaterialAlertDialogBuilder(act)
                    .setTitle("Uploading to GitHub")
                    .setCancelable(false)
                    .setView(binding.getRoot())
                    .create();
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            Activity act = activityRef.get();
            if (act == null) return "Activity is null";

            try {
                // Step 1: Create backup
                publishProgress("Creating backup...");
                BackupFactory factory = new BackupFactory(scId);
                factory.setBackupLocalLibs(true);
                factory.setBackupCustomBlocks(true);
                factory.backup(act, appName);

                File backupFile = factory.getOutFile();
                if (backupFile == null) {
                    return "Failed to create backup: " + factory.getError();
                }

                // Step 2: Read and encode file
                publishProgress("Encoding file...");
                byte[] fileBytes;
                try (RandomAccessFile raf = new RandomAccessFile(backupFile, "r")) {
                    fileBytes = new byte[(int) raf.length()];
                    raf.readFully(fileBytes);
                }
                String base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP);

                String token = GitHubTokenManager.getToken(act);
                String username = GitHubTokenManager.getUsername(act);
                GitHubApiClient client = new GitHubApiClient(token);

                // Step 3: Check if repo exists, create if not
                publishProgress("Checking repository...");
                final String[] repoCheckResult = {null};
                final boolean[] repoCheckDone = {false};

                client.getRepo(username, repoName, response -> {
                    repoCheckResult[0] = response;
                    repoCheckDone[0] = true;
                });

                // Wait for async response
                int timeout = 30;
                while (!repoCheckDone[0] && timeout > 0) {
                    Thread.sleep(500);
                    timeout--;
                }
                if (timeout <= 0) return "Timeout checking repository";

                boolean repoExists = repoCheckResult[0] != null &&
                        repoCheckResult[0].contains("\"full_name\"");

                if (!repoExists) {
                    publishProgress("Creating repository...");
                    final boolean[] createDone = {false};
                    final String[] createResult = {null};

                    client.createRepo(repoName, description, isPrivate, response -> {
                        createResult[0] = response;
                        createDone[0] = true;
                    });

                    timeout = 30;
                    while (!createDone[0] && timeout > 0) {
                        Thread.sleep(500);
                        timeout--;
                    }
                    if (timeout <= 0) return "Timeout creating repository";
                    if (createResult[0] == null) return "Failed to create repository";

                    // Wait a bit for GitHub to process
                    Thread.sleep(2000);
                }

                // Step 4: Check if file already exists (to get SHA for update)
                publishProgress("Checking existing file...");
                String fileName = repoName + ".swb";
                final String[] fileInfoResult = {null};
                final boolean[] fileInfoDone = {false};

                client.getFileInfo(username, repoName, fileName, response -> {
                    fileInfoResult[0] = response;
                    fileInfoDone[0] = true;
                });

                timeout = 30;
                while (!fileInfoDone[0] && timeout > 0) {
                    Thread.sleep(500);
                    timeout--;
                }

                String sha = null;
                if (fileInfoResult[0] != null && fileInfoResult[0].contains("\"sha\"")) {
                    try {
                        Map<String, Object> fileInfo = new Gson().fromJson(fileInfoResult[0], Map.class);
                        sha = (String) fileInfo.get("sha");
                    } catch (Exception ignored) {
                    }
                }

                // Step 5: Upload file
                publishProgress("Uploading to GitHub...");
                String commitMessage = sha != null ?
                        "Update " + appName + " backup" :
                        "Add " + appName + " backup";

                final boolean[] uploadDone = {false};
                final String[] uploadResult = {null};

                client.uploadFile(username, repoName, fileName, base64Content, sha, commitMessage, response -> {
                    uploadResult[0] = response;
                    uploadDone[0] = true;
                });

                timeout = 60;
                while (!uploadDone[0] && timeout > 0) {
                    Thread.sleep(500);
                    timeout--;
                }
                if (timeout <= 0) return "Timeout uploading file";
                if (uploadResult[0] == null) return "Failed to upload file";

                // Clean up temp backup
                backupFile.delete();
                File parentDir = backupFile.getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    String[] children = parentDir.list();
                    if (children != null && children.length == 0) {
                        parentDir.delete();
                    }
                }

                success = true;
                return "https://github.com/" + username + "/" + repoName;

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
                SketchwareUtil.toast("Uploaded successfully!\n" + result, Toast.LENGTH_LONG);
            } else {
                SketchwareUtil.toastError("Upload failed: " + result, Toast.LENGTH_LONG);
            }
        }
    }
}
