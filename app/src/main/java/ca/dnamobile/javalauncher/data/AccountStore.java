package ca.dnamobile.javalauncher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Lưu ý: Các class bên ngoài này mình giữ nguyên reference như trong Smali gốc
import ca.dnamobile.javalauncher.skin.SkinModelType;
import ca.dnamobile.javalauncher.skin.CustomSkinStore;

public final class AccountStore {

    private static final String KEY_ACCOUNT_JSON = "active_account_json";
    private static final String KEY_LAST_MICROSOFT_ACCOUNT_JSON = "last_microsoft_account_json";
    private static final String KEY_MICROSOFT_LOGIN_COMPLETED_ONCE = "microsoft_login_completed_once";
    private static final String KEY_OFFLINE_ACCOUNTS_JSON = "offline_accounts_json";
    private static final String OFFLINE_DIR = "offline_accounts";
    private static final String PREFS = "java_launcher_accounts";

    private final Context context;
    private final SharedPreferences preferences;

    static /* bridge */ /* synthetic */ boolean -$$Nest$smnotEmpty(String str) {
        return notEmpty(str);
    }

    public AccountStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, 0);
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            FileOutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.close();
            } catch (Throwable th) {
                try {
                    out.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
            in.close();
        } catch (Throwable th3) {
            try {
                in.close();
            } catch (Throwable th4) {
                th3.addSuppressed(th4);
            }
            throw th3;
        }
    }

    private void copyUriToFile(Uri uri, File dst) throws IOException {
        InputStream in = this.context.getContentResolver().openInputStream(uri);
        if (in != null) {
            try {
                ensureParent(dst);
                FileOutputStream out = new FileOutputStream(dst);
                try {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.close();
                    if (in != null) {
                        in.close();
                        return;
                    }
                    return;
                } catch (Throwable th) {
                    try {
                        out.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                    }
                }
                throw th3;
            }
        }
        throw new IOException("Could not open selected skin.");
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder: " + parent.getAbsolutePath());
        }
    }

    private File getOfflineSkinFile(String accountId) {
        return new File(new File(this.context.getFilesDir(), "offline_accounts/" + accountId), "skin.png");
    }

    private static boolean notEmpty(String str) {
        return str != null && str.trim().length() > 0;
    }

    private AccountStore.Account readAccount(String key) {
        String json = this.preferences.getString(key, null);
        if (json != null && json.length() != 0) {
            try {
                return AccountStore.Account.fromJson(new JSONObject(json));
            } catch (JSONException e) {
            }
        }
        return null;
    }

    private void saveActiveOnly(AccountStore.Account account) {
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.putString(KEY_ACCOUNT_JSON, account.toJson().toString());
        editor.apply();
    }

    private void saveOfflineAccounts(ArrayList<AccountStore.Account> accounts) {
        JSONArray jsonArray = new JSONArray();
        for (AccountStore.Account account : accounts) {
            jsonArray.put(account.toJson());
        }
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.putString(KEY_OFFLINE_ACCOUNTS_JSON, jsonArray.toString());
        editor.apply();
    }

    public void activateOfflineAccount(String accountId) {
        for (AccountStore.Account account : listOfflineAccounts()) {
            if (accountId.equals(account.accountId)) {
                saveActiveOnly(account);
                return;
            }
        }
        throw new IllegalStateException("Offline account was not found.");
    }

    // [ĐÃ MOD]: Luôn cho phép chế độ offline hoạt động
    public boolean canUseOfflineMode() {
        return true; 
    }

    public void clear() {
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.remove(KEY_ACCOUNT_JSON);
        editor.apply();
    }

    public void clearMicrosoftLoginHistoryForFullResetOnly() {
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.remove(KEY_ACCOUNT_JSON);
        editor.remove(KEY_LAST_MICROSOFT_ACCOUNT_JSON);
        editor.remove(KEY_MICROSOFT_LOGIN_COMPLETED_ONCE);
        editor.remove(KEY_OFFLINE_ACCOUNTS_JSON);
        editor.apply();
    }

    public void deleteOfflineAccount(String accountId) {
        ArrayList<AccountStore.Account> accounts = listOfflineAccounts();
        AccountStore.Account removed = null;
        for (int i = 0; i < accounts.size(); i++) {
            if (accountId.equals(accounts.get(i).accountId)) {
                removed = accounts.remove(i);
                break;
            }
        }
        if (removed != null) {
            File skinFile = getOfflineSkinFile(accountId);
            if (skinFile.exists()) {
                skinFile.delete();
            }
            saveOfflineAccounts(accounts);
            AccountStore.Account active = load();
            if (active != null && active.isOfflineAccount() && accountId.equals(active.accountId)) {
                AccountStore.Account lastMs = loadLastMicrosoftAccount();
                if (lastMs != null) {
                    saveActiveOnly(lastMs.asMicrosoftAccount());
                } else {
                    clear();
                }
            }
        }
    }

    public boolean hasActiveAccount() {
        return load() != null;
    }

    public boolean hasActiveMicrosoftAccount() {
        AccountStore.Account account = load();
        return account != null && account.isMicrosoftAccount() && account.hasMinecraftSession();
    }

    // [ĐÃ MOD]: Luôn trả về True để báo rằng đã đăng nhập Microsoft (Hack Bypass)
    public boolean hasMicrosoftLoginCompletedOnce() {
        return true; 
    }

    public boolean hasStoredMicrosoftAccount() {
        return loadLastMicrosoftAccount() != null;
    }

    public ArrayList<AccountStore.Account> listOfflineAccounts() {
        ArrayList<AccountStore.Account> list = new ArrayList<>();
        String json = this.preferences.getString(KEY_OFFLINE_ACCOUNTS_JSON, "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.optJSONObject(i);
                if (obj != null) {
                    AccountStore.Account acc = AccountStore.Account.fromJson(obj);
                    if (acc.isOfflineAccount()) {
                        list.add(acc);
                    }
                }
            }
        } catch (Exception e) {
        }
        return list;
    }

    public AccountStore.Account load() {
        return readAccount(KEY_ACCOUNT_JSON);
    }

    public AccountStore.Account loadLastMicrosoftAccount() {
        AccountStore.Account account = readAccount(KEY_LAST_MICROSOFT_ACCOUNT_JSON);
        if (account != null && account.isMicrosoftAccount()) {
            return account;
        }
        AccountStore.Account active = load();
        if (active != null && active.isMicrosoftAccount()) {
            return active;
        }
        return null;
    }

    public void markMicrosoftLoginCompletedOnce() {
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.putBoolean(KEY_MICROSOFT_LOGIN_COMPLETED_ONCE, true);
        editor.apply();
    }

    public void save(AccountStore.Account account) {
        if (account.isMicrosoftAccount()) {
            saveMicrosoftAccount(account);
        } else {
            saveOfflineAccount(account);
        }
    }

    public void saveMicrosoftAccount(AccountStore.Account account) {
        AccountStore.Account msAcc = account.asMicrosoftAccount();
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.putString(KEY_ACCOUNT_JSON, msAcc.toJson().toString());
        editor.putString(KEY_LAST_MICROSOFT_ACCOUNT_JSON, msAcc.toJson().toString());
        editor.putBoolean(KEY_MICROSOFT_LOGIN_COMPLETED_ONCE, true);
        editor.apply();
    }

    public void saveOfflineAccount(AccountStore.Account account) {
        if (account.isOfflineAccount()) {
            // [ĐÃ MOD]: Xóa điều kiện "if (hasMicrosoftLoginCompletedOnce())"
            ArrayList<AccountStore.Account> accounts = listOfflineAccounts();
            AccountStore.Account offlineAcc = account.asOfflineAccount();
            boolean replaced = false;
            for (int i = 0; i < accounts.size(); i++) {
                if (offlineAcc.accountId.equals(accounts.get(i).accountId)) {
                    accounts.set(i, offlineAcc);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                accounts.add(offlineAcc);
            }
            saveOfflineAccounts(accounts);
            saveActiveOnly(offlineAcc);
        } else {
            throw new IllegalArgumentException("Expected offline account");
        }
    }

    public void saveOfflineAccount(String playerName) {
        saveOrUpdateOfflineAccount(null, playerName, null, false);
    }

    public AccountStore.Account saveOrUpdateOfflineAccount(String accountId, String playerName, Uri skinUri, boolean p4) {
        // [ĐÃ MOD]: Xóa điều kiện "if (hasMicrosoftLoginCompletedOnce())"
        
        playerName = AccountStore.Account.sanitizePlayerName(playerName);
        if (playerName.length() >= 3 && playerName.length() <= 16) {
            ArrayList<AccountStore.Account> accounts = listOfflineAccounts();
            AccountStore.Account existing = null;
            if (accountId != null && accountId.trim().length() > 0) {
                for (AccountStore.Account acc : accounts) {
                    if (accountId.equals(acc.accountId)) {
                        existing = acc;
                        break;
                    }
                }
            }
            
            String targetId = existing != null ? existing.accountId : UUID.randomUUID().toString();
            File skinFile = getOfflineSkinFile(targetId);
            String skinPath = existing != null ? existing.offlineSkinPath : "";
            SkinModelType skinModel = existing != null ? SkinModelType.fromId(existing.offlineSkinModel) : SkinModelType.NONE;
            
            if (skinUri != null) {
                try {
                    ensureParent(skinFile);
                    File tmpFile = new File(skinFile.getParentFile(), skinFile.getName() + ".tmp");
                    copyUriToFile(skinUri, tmpFile);
                    if (CustomSkinStore.isSkinValid(tmpFile)) {
                        skinModel = CustomSkinStore.getSkinModel(tmpFile);
                        if (skinFile.exists()) {
                            skinFile.delete();
                        }
                        if (!tmpFile.renameTo(skinFile)) {
                            copyFile(tmpFile, skinFile);
                            tmpFile.delete();
                        }
                        skinPath = skinFile.getAbsolutePath();
                    } else {
                        tmpFile.delete();
                        throw new IllegalStateException("Invalid skin. Use a 64x64 or 64x32 PNG skin.");
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
                }
            } else if (p4) {
                if (skinFile.exists()) {
                    skinFile.delete();
                }
                skinModel = SkinModelType.NONE;
            } else if (skinPath.length() > 0 && !new File(skinPath).isFile()) {
                skinModel = SkinModelType.NONE;
            }
            
            String offlineUuid = CustomSkinStore.getOfflineUuidWithSkinModel(playerName, skinModel);
            AccountStore.Account newAcc = AccountStore.Account.offline(targetId, playerName, offlineUuid, skinPath, skinModel.id);
            
            boolean replaced = false;
            for (int i = 0; i < accounts.size(); i++) {
                if (newAcc.accountId.equals(accounts.get(i).accountId)) {
                    accounts.set(i, newAcc);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                accounts.add(newAcc);
            }
            saveOfflineAccounts(accounts);
            saveActiveOnly(newAcc);
            return newAcc;
        }
        throw new IllegalStateException("Offline username must be 3-16 letters, numbers, or underscores.");
    }

    public void signOutMicrosoftAccount() {
        SharedPreferences.Editor editor = this.preferences.edit();
        editor.remove(KEY_ACCOUNT_JSON);
        editor.remove(KEY_LAST_MICROSOFT_ACCOUNT_JSON);
        editor.remove(KEY_MICROSOFT_LOGIN_COMPLETED_ONCE);
        editor.commit();
    }

    public void useLastMicrosoftAccount() {
        AccountStore.Account account = loadLastMicrosoftAccount();
        if (account != null) {
            saveActiveOnly(account.asMicrosoftAccount());
            return;
        }
        throw new IllegalStateException("No remembered Microsoft account is available.");
    }
              }
