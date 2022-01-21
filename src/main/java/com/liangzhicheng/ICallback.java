package com.liangzhicheng;

import okhttp3.Call;

public interface ICallback {

    void onSuccessful(Call call, String data);

    void onFailure(Call call, String errorMessage);

}
