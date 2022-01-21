package com.liangzhicheng;

import okhttp3.Call;

public class Test {

    public static void main(String[] args) {
        //get请求，方法顺序按照以下方式，get/post必须放在倒数第二，同步/异步放在倒数第一，否则不会正确执行
        OkHttpFactory.builder()
                .url("请求地址，http/https都可")
                .addParam("参数名", "参数值") //参数可多个
                .addHeader("Content-Type", "application/json; charset=utf-8") //请求头可多个
                .sendGet()
                //可选择同步/异步
                .sync();
//                .async();

        //post请求
        OkHttpFactory.builder()
                .url("请求地址，http/https都可")
                .method("POST/PUT/DELETE")
                .addParam("参数名", "参数值")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .sendRequest(true)
                .sync();

        //选择异步有两个方法，返回值和带接口回调
        OkHttpFactory.builder()
                .url("")
                .sendRequest(false)
                .async();

        OkHttpFactory.builder()
                .url("")
                .sendRequest(false)
                .async(new ICallback() {
                    @Override
                    public void onSuccessful(Call call, String data) {
                        //请求成功后处理
                    }
                    @Override
                    public void onFailure(Call call, String errorMessage) {
                        //请求失败后处理
                    }
                });
    }

}
