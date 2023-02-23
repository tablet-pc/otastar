package cn.synzbtech.ota.service;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import org.greenrobot.eventbus.EventBus;
import org.xutils.http.RequestParams;
import org.xutils.x;

import cn.synzbtech.ota.OtaApplication;
import cn.synzbtech.ota.core.Api;
import cn.synzbtech.ota.core.DeviceInfoWrapper;
import cn.synzbtech.ota.core.entity.ApkVersion;
import cn.synzbtech.ota.core.entity.OtaVersion;
import cn.synzbtech.ota.core.entity.ResultWrapper;
import cn.synzbtech.ota.ui.MainActivity;
import cn.synzbtech.ota.utils.NotificationUtils;
import cn.synzbtech.ota.core.entity.ApkUpgradeMainEvent;
import cn.synzbtech.ota.core.entity.PackUpgradeMainEvent;
import cn.synzbtech.ota.utils.OtaUtils;
import cn.synzbtech.ota.utils.PreferenceUtils;
import lombok.Setter;

/**
 *
 * 升级检测服务。用于检测程序apk 或者 系统ota是否需要升级。通过启动一个线程 {@see UpdateCheckWorker } 来完成具体的检测工作
 *
 * @author hyy owen
 */
public class CheckUpdateService extends Service {

    private static final String TAG = "CheckUpdateService";
    private Thread checkUpdateThread;
    private final UpdateCheckWorker updateCheckWorker = new UpdateCheckWorker();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();

        NotificationUtils.getInstance();

        if(checkUpdateThread ==null) {
            checkUpdateThread = new Thread(updateCheckWorker);
            checkUpdateThread.start();
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateCheckWorker.stop();
    }

    /**
     * 升级检测工作类。
     */
    @Setter
    private class UpdateCheckWorker implements Runnable {

        private int apkState = 0; // app apk 升级状态

        private int packState = 0; // 系统 ota 升级状态

        private int CHECK_INTERVAL = 1000 * 10; // 升级检测时间间隔。


        private ApkVersion getApkVersion() {

            if(DeviceInfoWrapper.deviceInfo ==null) {
                Log.d(TAG, "device info is null,can not get apk version");
                return null;
            }

            RequestParams params = new RequestParams(Api.host + Api.URL.GET_APK_VERSION);
            params.addQueryStringParameter("wifiMac", DeviceInfoWrapper.deviceInfo.getWifiMacAddress());
            try {
                String responseJSON = x.http().postSync(params, String.class);
                ResultWrapper<ApkVersion> resultWrapper = JSON.parseObject(responseJSON, new TypeReference<ResultWrapper<ApkVersion>>(){}.getType());
                Log.d(TAG, "response code " + resultWrapper.getCode()+" message " + resultWrapper.getMsg());
                return resultWrapper.getData();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                return null;
            }
        }

        private OtaVersion getOtaVersion() {
            if(DeviceInfoWrapper.deviceInfo ==null) {
                Log.d(TAG, "device info is null,can not get ota version");
                return null;
            }

            RequestParams params = new RequestParams(Api.host + Api.URL.GET_OAT_VERSION);
            params.addQueryStringParameter("wifiMac", DeviceInfoWrapper.deviceInfo.getWifiMacAddress());
            try {
                String responseJSON = x.http().postSync(params, String.class);
                ResultWrapper<OtaVersion> resultWrapper = JSON.parseObject(responseJSON, new TypeReference<ResultWrapper<OtaVersion>>(){}.getType());
                Log.d(TAG, "response code " + resultWrapper.getCode()+" message " + resultWrapper.getMsg());
                return resultWrapper.getData();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                return null;
            }
        }

        volatile boolean upgradeRunning = true;

        @Override
        public void run() {

            while (upgradeRunning) {
                try {
                    Thread.sleep(CHECK_INTERVAL);

                    Log.d(TAG, "start get upgrade. is apk upgrade " + OtaApplication.apkUpgradeState+", is pack upgrade " + OtaApplication.packUpgradeState);

                    if(OtaApplication.apkUpgradeState != Api.UpgradeState.CHECK) {
                        if(OtaApplication.apkUpgradeState==Api.UpgradeState.NEED_DOWNLOAD) {
                            ApkVersion apkVersion = getApkVersion();
                            if(apkVersion==null) { // 取消了。通知现在停止，具体停止请查看 MainActivity packUpgradeEvent 方法为-2的分支
                                OtaApplication.apkUpgradeState = Api.UpgradeState.CHECK;
                                PreferenceUtils.getInstance().setApkUpgradeState(Api.UpgradeState.CHECK);
                                EventBus.getDefault().post(new ApkUpgradeMainEvent(Api.DownloadState.CANCELED));
                            }


                        } else if(OtaApplication.apkUpgradeState >= Api.UpgradeState.DOWNLOAD_COMPLETE) {
                            apkState++;
                            if(apkState>=3) {
                                OtaApplication.apkUpgradeState = Api.UpgradeState.CHECK;
                                PreferenceUtils.getInstance().setApkUpgradeState(Api.UpgradeState.CHECK);
                            }
                        }
                        continue;
                    }

                    if(OtaApplication.packUpgradeState != Api.UpgradeState.CHECK) {

                        //如果正在下载
                        if(OtaApplication.packUpgradeState==Api.UpgradeState.NEED_DOWNLOAD) {
                            //获取是否取消了下载。
                            OtaVersion packVersion = getOtaVersion();
                            if(packVersion==null) {// 取消了。通知现在停止，具体停止请查看 MainActivity packUpgradeEvent 方法为-2的分支
                                OtaApplication.packUpgradeState = Api.UpgradeState.CHECK;
                                PreferenceUtils.getInstance().setPackUpgradeState(Api.UpgradeState.CHECK);
                                EventBus.getDefault().post(new PackUpgradeMainEvent(Api.DownloadState.CANCELED));
                            }

                        } else if(OtaApplication.packUpgradeState>= Api.UpgradeState.DOWNLOAD_COMPLETE) {
                            packState++;
                        }

                        if(packState>=3) {
                            OtaApplication.packUpgradeState = Api.UpgradeState.CHECK;
                            PreferenceUtils.getInstance().setPackUpgradeState(Api.UpgradeState.CHECK);
                        }
                        continue;
                    }

                    ApkVersion apkVersion = getApkVersion();
                    if(apkVersion!=null) {
                        OtaApplication.apkUpgradeState = Api.UpgradeState.NEED_DOWNLOAD;
                        PreferenceUtils.getInstance().setApkUpgradeState(Api.UpgradeState.NEED_DOWNLOAD);
                        PreferenceUtils.getInstance().setApkUpgradeUrl(apkVersion.getUrl());

                        Log.d(TAG, "apk upgrade version " + apkVersion.getUrl());

                        boolean mainForeground = OtaUtils.isActivityForeground(CheckUpdateService.this, MainActivity.class.getSimpleName());
                        if (mainForeground) {
                            EventBus.getDefault().post(new ApkUpgradeMainEvent());
                        } else {
                            NotificationUtils.getInstance().sendNotification("升级通知", "有版本，请前往升级", "");
                        }
                    }

                    if(OtaApplication.apkUpgradeState != Api.UpgradeState.CHECK) {
                        continue;
                    }

                    OtaVersion packVersion = getOtaVersion();
                    if(packVersion!=null) {
                        //下载pack包
                        OtaApplication.packUpgradeState =  Api.UpgradeState.NEED_DOWNLOAD;
                        Log.d(TAG, "pack upgrade version " + packVersion.getUrl());
                        PreferenceUtils.getInstance().setPackUpgradeState(Api.UpgradeState.NEED_DOWNLOAD);
                        // 设置OTA下载地址,格式：url,md5
                        PreferenceUtils.getInstance().setPackUpgradeUrl(packVersion.getUrl() + "," + packVersion.getMd5());

                        boolean mainForeground = OtaUtils.isActivityForeground(CheckUpdateService.this, MainActivity.class.getSimpleName());
                        if (mainForeground) {
                            EventBus.getDefault().post(new PackUpgradeMainEvent());
                        } else {
                            NotificationUtils.getInstance().sendNotification("升级通知", "有新OTA版本，请前往升级", "");
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                    upgradeRunning = false;
                }
            }
        }

        public void stop() {
            upgradeRunning = false;
        }
    }
}
