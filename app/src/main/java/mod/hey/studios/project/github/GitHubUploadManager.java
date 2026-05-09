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
import java.util.Map;

import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogGithubLoginBinding;
import pro.sketchware.databinding.DialogGithubUploadBinding;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.Network;
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

        // Show current login status
        if (GitHubTokenManager.isLoggedIn(activity)) {
            String currentUser = GitHubTokenManager.getUsername(activity);
            binding.tvStatus.setVisibility(android.view.View.VISIBLE);
            binding.tvStatus.setText("Currently logged in as: " + currentUser + "\nEnter a new token to change account.");
        }

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

            // Validate token in background
            new Thread(() -> {
                try {
                    GitHubApiClient client = new GitHubApiClient(token);
                    Network.SyncResponse response = client.getUserSync();

                    activity.runOnUiThread(() -> {
                        if (response.isSuccessful() && response.body != null) {
                            try {
                                Map<String, Object> user = new Gson().fromJson(response.body, Map.class);
                                String username = (String) user.get("login");
                                if (username != null) {
                                    GitHubTokenManager.saveToken(activity, token);
                                    GitHubTokenManager.saveUsername(activity, username);
                                    SketchwareUtil.toast("Logged in as " + username);
                                    if (onSuccess != null) onSuccess.run();
                                } else {
                                    SketchwareUtil.toastError("Invalid response from GitHub");
                                }
                            } catch (Exception e) {
                                SketchwareUtil.toastError("Failed to parse response: " + e.getMessage());
                            }
                        } else {
                            SketchwareUtil.toastError("Invalid token (HTTP " + response.code + ")");
                        }
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() ->
                            SketchwareUtil.toastError("Connection failed: " + e.getMessage()));
                }
            }).start();
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void showUploadDialog(String scId, String appName) {
        DialogGithubUploadBinding binding = DialogGithubUploadBinding.inflate(LayoutInflater.from(activity));

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
            if (act == null) return "Activity not available";

            try {
                String token = GitHubTokenManager.getToken(act);
                String username = GitHubTokenManager.getUsername(act);
                if (token == null || username == null) {
                    return "Not logged in to GitHub";
                }

                GitHubApiClient client = new GitHubApiClient(token);

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

                // Step 3: Check if repo exists
                publishProgress("Checking repository...");
                Network.SyncResponse repoCheck = client.getRepoSync(username, repoName);

                if (!repoCheck.isSuccessful()) {
                    // Repo doesn't exist, create it
                    publishProgress("Creating repository '" + repoName + "'...");
                    Network.SyncResponse createResponse = client.createRepoSync(repoName, description, isPrivate);

                    if (!createResponse.isSuccessful()) {
                        String errorMsg = parseGitHubError(createResponse.code, createResponse.body);
                        return "Failed to create repository: " + errorMsg;
                    }

                    // Wait for GitHub to initialize the repo
                    Thread.sleep(3000);
                }

                // Step 4: Check if file already exists (to get SHA for update)
                publishProgress("Preparing upload...");
                String fileName = repoName + ".swb";
                String sha = null;

                Network.SyncResponse fileCheck = client.getFileInfoSync(username, repoName, fileName);
                if (fileCheck.isSuccessful() && fileCheck.body != null) {
                    try {
                        Map<String, Object> fileInfo = new Gson().fromJson(fileCheck.body, Map.class);
                        sha = (String) fileInfo.get("sha");
                    } catch (Exception ignored) {
                    }
                }

                // Step 5: Upload file
                publishProgress("Uploading " + fileName + " to GitHub...");
                String commitMessage = sha != null
                        ? "Update " + appName + " backup"
                        : "Add " + appName + " backup";

                Network.SyncResponse uploadResponse = client.uploadFileSync(
                        username, repoName, fileName, base64Content, sha, commitMessage);

                if (!uploadResponse.isSuccessful()) {
                    String errorMsg = parseGitHubError(uploadResponse.code, uploadResponse.body);
                    return "Upload failed: " + errorMsg;
                }

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

        private String parseGitHubError(int code, String responseBody) {
            if (code == 401) {
                return "Token expired or invalid. Go to GitHub menu > Login to update your token.";
            }
            if (code == 403) {
                return "Token doesn't have permission. Generate a new token with 'repo' scope enabled.\nGitHub > Settings > Developer settings > Personal access tokens > Tokens (classic)";
            }
            if (code == 422) {
                return "Repository name already exists or is invalid.";
            }
            if (responseBody == null) return "No response from server";
            try {
                Map<String, Object> error = new Gson().fromJson(responseBody, Map.class);
                if (error.containsKey("message")) {
                    return (String) error.get("message");
                }
            } catch (Exception ignored) {
            }
            return responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody;
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
