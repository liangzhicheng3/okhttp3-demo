package com.liangzhicheng;

import cn.hutool.json.JSONUtil;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 使用建造者模式实现OkHttp3向服务发送请求
 */
public class OkHttpFactory {

    private static volatile OkHttpClient okHttpClient = null;
    private static volatile Semaphore semaphore = null;

    private Request.Builder request;

    private Map<String, String> headerMap;
    private Map<String, String> paramMap;

    private String url;
    private String method;

    /**
     * 创建OkHttpFactory
     * @return
     */
    public static OkHttpFactory builder(){
        return new OkHttpFactory();
    }

    /**
     * 赋值url
     * @param url 地址
     * @return
     */
    public OkHttpFactory url(String url){
        this.url = url;
        return this;
    }

    /**
     * 赋值method
     * @param method 请求方式
     * @return
     */
    public OkHttpFactory method(String method){
        this.method = method;
        return this;
    }

    /**
     * 添加参数
     * @param key 参数名
     * @param value 参数值
     * @return
     */
    public OkHttpFactory addParam(String key, String value){
        if(paramMap == null){
            paramMap = new LinkedHashMap<>(16);
        }
        paramMap.put(key, value);
        return this;
    }

    /**
     * 添加请求头
     * @param key 参数名
     * @param value 参数值
     * @return
     */
    public OkHttpFactory addHeader(String key, String value){
        if(headerMap == null){
            headerMap = new LinkedHashMap<>(16);
        }
        headerMap.put(key, value);
        return this;
    }

    /**
     * 初始化get
     * @return
     */
    public OkHttpFactory sendGet(){
        StringBuilder splice = new StringBuilder(url);
        if(paramMap != null){
            splice.append("?");
            try{
                for(Map.Entry<String, String> entry : paramMap.entrySet()){
                    splice.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue(), "UTF-8"))
                            .append("&");
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            splice.deleteCharAt(splice.length() - 1);
        }
        request = new Request.Builder().get().url(splice.toString());
        return this;
    }

    /**
     * 初始化post/put/delete
     * @param isJson true表示json方式提交数据
     *               false表示表单方式提交数据
     * @return
     */
    public OkHttpFactory sendRequest(boolean isJson){
        RequestBody requestBody = null;
        if(isJson){
            if(paramMap != null){
                String json = JSONUtil.toJsonStr(paramMap);
                requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
            }
        }else{
            FormBody.Builder body = new FormBody.Builder();
            if(paramMap != null){
                paramMap.forEach(body::add);
            }
            requestBody = body.build();
        }
        request = new Request.Builder();
        if("POST".equals(method)){
            request.post(requestBody);
        }else if("PUT".equals(method)){
            request.put(requestBody);
        }else if("DELETE".equals(method)){
            request.delete(requestBody);
        }
        request.url(url);
        return this;
    }

    /**
     * 同步请求
     * @return
     */
    public String sync(){
        setHeader(request);
        try{
            Response response = okHttpClient.newCall(request.build()).execute();
            assert response.body() != null;
            return response.body().toString();
        }catch(IOException e){
            e.printStackTrace();
            return "请求失败：" + e.getMessage();
        }
    }

    /**
     * 异步请求，有返回值
     * @return
     */
    public String async(){
        setHeader(request);
        StringBuffer splice = new StringBuffer("");
        okHttpClient.newCall(request.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                splice.append("请求出错：")
                        .append(e.getMessage());
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                assert response.body() != null;
                splice.append(response.body().string());
                getSemaphoreInstance().release();
            }
        });
        try{
            getSemaphoreInstance().acquire();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        return splice.toString();
    }

    /**
     * 异步请求，带有接口回调
     * @param callback 接口回调
     * @return
     */
    public void async(ICallback callback){
        setHeader(request);
        okHttpClient.newCall(request.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onFailure(call, e.getMessage());
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                assert response.body() != null;
                callback.onSuccessful(call, response.body().string());
            }
        });
    }

    /**
     * 初始化OkHttpClient
     */
    private OkHttpFactory(){
        if(okHttpClient == null){
            synchronized(OkHttpFactory.class){
                if(okHttpClient == null){
                    TrustManager[] trustManagers = buildTrustManagers();
                    okHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .sslSocketFactory(createSSLSocketFactory(trustManagers), (X509TrustManager) trustManagers[0])
                            .hostnameVerifier((hostName, session) -> true)
                            .retryOnConnectionFailure(true)
                            .build();
                    addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36");
                }
            }
        }
    }

    /**
     * 构建信任管理器
     * @return
     */
    private static TrustManager[] buildTrustManagers(){
        return new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
    }

    /**
     * 创建安全连接工厂，用于https请求证书跳过
     * @param trustManagers 信任管理器
     * @return
     */
    private static SSLSocketFactory createSSLSocketFactory(TrustManager[] trustManagers){
        SSLSocketFactory sslSocketFactory = null;
        try{
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustManagers, new SecureRandom());
            sslSocketFactory = sslContext.getSocketFactory();
        }catch(Exception e){
            e.printStackTrace();
        }
        return sslSocketFactory;
    }


    /**
     * 用于异步请求时，控制访问线程数，返回结果
     * @return
     */
    private static Semaphore getSemaphoreInstance(){
        synchronized(OkHttpFactory.class){
            if(semaphore == null){
                semaphore = new Semaphore(0);
            }
        }
        return semaphore;
    }

    /**
     * 为request添加请求头
     * @param request
     */
    private void setHeader(Request.Builder request){
        if(headerMap != null){
            try{
                for(Map.Entry<String, String> entry : headerMap.entrySet()){
                    request.addHeader(entry.getKey(), entry.getValue());
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

}
