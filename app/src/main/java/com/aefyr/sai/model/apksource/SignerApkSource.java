package com.aefyr.sai.model.apksource;

import android.content.Context;
import android.util.Log;

import com.aefyr.pseudoapksigner.PseudoApkSigner;
import com.aefyr.sai.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SignerApkSource implements ApkSource {
    private static final String TAG = "SignerApkSource";
    private static final String FILE_NAME_PAST = "testkey.past";
    private static final String FILE_NAME_PRIVATE_KEY = "testkey.pk8";

    private ApkSource mWrappedApkSource;
    private Context mContext;
    private boolean mIsPrepared;
    private PseudoApkSigner mApkSigner;
    private File mCacheDirectory;

    private File mCurrentSignedApkFile;

    public SignerApkSource(Context c, ApkSource apkSource) {
        mContext = c;
        mWrappedApkSource = apkSource;
    }

    @Override
    public boolean nextApk() throws Exception {
        if (!mWrappedApkSource.nextApk()) {
            clearCache();
            return false;
        }

        if (!mIsPrepared) {
            checkAndPrepareSigningEnvironment();
            createCacheDir();
            mApkSigner = new PseudoApkSigner(new File(getSigningEnvironmentDir(), FILE_NAME_PAST), new File(getSigningEnvironmentDir(), FILE_NAME_PRIVATE_KEY));
        }

        mCurrentSignedApkFile = new File(mCacheDirectory, getApkName());
        mApkSigner.sign(mWrappedApkSource.openApkInputStream(), new FileOutputStream(mCurrentSignedApkFile));

        return true;
    }

    @Override
    public InputStream openApkInputStream() throws Exception {
        return new FileInputStream(mCurrentSignedApkFile);
    }

    @Override
    public long getApkLength() {
        return mCurrentSignedApkFile.length();
    }

    @Override
    public String getApkName() throws Exception {
        return mWrappedApkSource.getApkName();
    }

    private void checkAndPrepareSigningEnvironment() throws Exception {
        File signingEnvironment = getSigningEnvironmentDir();
        File pastFile = new File(signingEnvironment, FILE_NAME_PAST);
        File privateKeyFile = new File(signingEnvironment, FILE_NAME_PRIVATE_KEY);

        if (pastFile.exists() && privateKeyFile.exists()) {
            mIsPrepared = true;
            return;
        }

        Log.d(TAG, "Preparing signing environment...");
        signingEnvironment.mkdir();

        IOUtils.copyFileFromAssets(mContext, FILE_NAME_PAST, pastFile);
        IOUtils.copyFileFromAssets(mContext, FILE_NAME_PRIVATE_KEY, privateKeyFile);

        mIsPrepared = true;
    }

    private File getSigningEnvironmentDir() {
        return new File(mContext.getFilesDir(), "signing");
    }

    private void createCacheDir() {
        mCacheDirectory = new File(mContext.getCacheDir(), String.valueOf(System.currentTimeMillis()));
        mCacheDirectory.mkdirs();
    }

    private void clearCache() {
        if (mCacheDirectory != null) {
            deleteFile(mCacheDirectory);
        }
    }

    private void deleteFile(File f) {
        if (f.isDirectory()) {
            for (File child : f.listFiles())
                deleteFile(child);
        }
        f.delete();
    }
}
