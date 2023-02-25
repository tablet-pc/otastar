package cn.synzbtech.ota.core.network;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.synzbtech.ota.AppConfig;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * http client is a network request utility class. This class implements both synchronous and asynchronous get and post methods.
 *
 * When you send a request, you first need to add appId and secretKey to the header. Please refer to the Readme file for appid and secretKey
 *
 * @author dennis@we-signage.com
 *
 */
public class HyyHttpClient {
    private static final String TAG ="HyyHttpClient";
    public String HOST = "http://192.168.1.11:8081/api/"; //Address of the host providing the service, http protocol

    public String APPID = "1629375064388136961";
    public String SECRET_EY = "ZXlKMGVYQWlPaUpLVjFRaUxDSmhiR2NpT2lKSVV6STFOaUo5LmV5SmhjSEJKWkNJNklqRTJNamt6TnpVd05qUXpPRGd4TXpZNU5qRWlMQ0oxYzJWeVRtRnRaU0k2SW1Ga2JXbHVJaXdpWlhod0lqb3lORFkyTWpJMk5qWXlMQ0oxYzJWeVNXUWlPaUl4SW4wLm5LVXBjZU9icXlzRFRZOElNb1E3TnRkeU5PYmxMRVZJUWRobHZzeERtalE=";

    private static HyyHttpClient mInstance;

    public static synchronized HyyHttpClient getInstance(){

        if(mInstance==null){
            mInstance = new HyyHttpClient();
            if(StringUtils.isNotEmpty(AppConfig.HOST)){
                mInstance.host(AppConfig.HOST);
            }
            if(StringUtils.isNotEmpty(AppConfig.APPID)){
                mInstance.appid(AppConfig.APPID);
            }
            if(StringUtils.isNotEmpty(AppConfig.SECRET_KEY)){
                mInstance.secretKey(AppConfig.SECRET_KEY);
            }
        }
        return mInstance;
    }

    private HyyHttpClient(){

    }

    public HyyHttpClient host(String host) {
        this.HOST = host;
        return this;
    }


    public HyyHttpClient appid(String appid){
        this.APPID = appid;
        return this;
    }

    public HyyHttpClient secretKey(String secretKey){
        this.SECRET_EY = secretKey;
        return this;
    }

    /**
     * Synchronous get request
     * @param uri
     * @param params
     * @return
     */
    public String get(String uri, Map<String, Object> params) {

        OkHttpClient client = new OkHttpClient();

        HttpUrl.Builder queryUrlBuilder = HttpUrl.get(HOST + uri).newBuilder();

        if(params!=null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                queryUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue() + "");
            }
        }

        Request request = new Request.Builder()
                .header("appId", APPID)
                .header("secretKey", SECRET_EY)
                .url(queryUrlBuilder.build())
                .build();

        try {
            Response response = client.newCall(request).execute();
            if(response.body()==null){
                Log.e(TAG, "get request failed, response body is null");
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            Log.e(TAG, "get request failed", e);
        }
        return null;
    }


    /**
     * Synchronous post request
     * @param uri
     * @param params a pojo or map. if json = false, this field must is hashMap , else json=true this field is pojo or map object
     * @param json  if post json data, this value must assignment true.
     * @return
     */
    public String post(String uri, Object params, boolean json) {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody;

        if(json) {
            requestBody = RequestBody.create(
                    JSON.toJSONString(params),
                    MediaType.parse("application/json")
            );
        } else {
            if(!(params instanceof HashMap)) {
                Log.e(TAG, "post request failed, request params type invalidate");
                return null;
            }
             FormBody.Builder builder = new FormBody.Builder();
             for(Map.Entry<String, Object> entry: ((Map<String, Object>) params).entrySet()){
                 builder.add(entry.getKey(), entry.getValue()+"");
             }
             requestBody = builder.build();
        }

        Request postRequest = new Request.Builder()
                .url(HOST+uri)
                .header("appId", APPID)
                .header("secretKey", SECRET_EY)
                .post(requestBody)
                .build();

        try {
            Response response = client.newCall(postRequest).execute();
            if(response.body()==null){
                Log.e(TAG, "post request failed, response body is null");
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            Log.e(TAG, "post request failed", e);
        }
        return null;
    }

    /**
     *
     */
    public static class URI {
        public static final String PUSH_DEVICE_INFO = "api/device/add";
    }

}
