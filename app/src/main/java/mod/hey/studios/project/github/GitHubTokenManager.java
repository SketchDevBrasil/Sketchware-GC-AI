package mod.hey.studios.project.github;

import android.content.Context;
import android.content.SharedPreferences;

public class GitHubTokenManager {
    private static final String PREFS_NAME = "github_settings";
    private static final String KEY_TOKEN = "github_pat";
    private static final String KEY_USERNAME = "github_username";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveToken(Context context, String token) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getToken(Context context) {
        return getPrefs(context).getString(KEY_TOKEN, null);
    }

    public static void clearToken(Context context) {
        getPrefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        String token = getToken(context);
        return token != null && !token.isEmpty();
    }

    public static void saveUsername(Context context, String username) {
        getPrefs(context).edit().putString(KEY_USERNAME, username).apply();
    }

    public static String getUsername(Context context) {
        return getPrefs(context).getString(KEY_USERNAME, null);
    }
}
