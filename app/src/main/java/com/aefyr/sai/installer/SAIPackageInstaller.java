package com.aefyr.sai.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import com.aefyr.sai.model.apksource.ApkSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SAIPackageInstaller {

    public enum InstallationStatus {
        QUEUED, INSTALLING, INSTALLATION_SUCCEED, INSTALLATION_FAILED
    }

    private Context mContext;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private ArrayDeque<QueuedInstallation> mInstallationQueue = new ArrayDeque<>();
    private ArrayList<InstallationStatusListener> mListeners = new ArrayList<>();
    private LongSparseArray<QueuedInstallation> mCreatedInstallationSessions = new LongSparseArray<>();

    private boolean mInstallationInProgress;
    private long mLastInstallationID = 0;
    private QueuedInstallation mOngoingInstallation;

    protected SAIPackageInstaller(Context c) {
        mContext = c.getApplicationContext();
    }

    protected Context getContext() {
        return mContext;
    }

    public interface InstallationStatusListener {
        void onStatusChanged(long installationID, InstallationStatus status, @Nullable String packageNameOrErrorDescription);
    }

    public void addStatusListener(InstallationStatusListener listener) {
        mListeners.add(listener);
    }

    public void removeStatusListener(InstallationStatusListener listener) {
        mListeners.remove(listener);
    }

    public long createInstallationSession(ApkSource apkSource) {
        long installationID = mLastInstallationID++;
        mCreatedInstallationSessions.put(installationID, new QueuedInstallation(getContext(), apkSource, installationID));
        return installationID;
    }

    public void startInstallationSession(long sessionID) {
        QueuedInstallation installation = mCreatedInstallationSessions.get(sessionID);
        mCreatedInstallationSessions.remove(sessionID);
        if (installation == null)
            return;

        mInstallationQueue.addLast(installation);
        dispatchSessionUpdate(installation.getId(), InstallationStatus.QUEUED, null);
        processQueue();
    }

    public boolean isInstallationInProgress() {
        return mInstallationInProgress;
    }

    private void processQueue() {
        if (mInstallationQueue.size() == 0 || mInstallationInProgress)
            return;

        QueuedInstallation installation = mInstallationQueue.removeFirst();
        mOngoingInstallation = installation;
        mInstallationInProgress = true;

        dispatchCurrentSessionUpdate(InstallationStatus.INSTALLING, null);

        mExecutor.execute(() -> installApkFiles(installation.getApkSource()));
    }

    protected abstract void installApkFiles(ApkSource apkSource);

    protected void installationCompleted() {
        mInstallationInProgress = false;
        mOngoingInstallation = null;
        processQueue();
    }

    protected void dispatchSessionUpdate(long sessionID, InstallationStatus status, String packageNameOrError) {
        mHandler.post(() -> {
            for (InstallationStatusListener listener : mListeners)
                listener.onStatusChanged(sessionID, status, packageNameOrError);
        });
    }

    protected void dispatchCurrentSessionUpdate(InstallationStatus status, String packageNameOrError) {
        dispatchSessionUpdate(mOngoingInstallation.getId(), status, packageNameOrError);
    }
}
