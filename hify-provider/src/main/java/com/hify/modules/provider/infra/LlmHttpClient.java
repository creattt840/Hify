package com.hify.modules.provider.infra;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LlmHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient standardClient;
    private final OkHttpClient streamClient;

    public LlmHttpClient() {
        this.standardClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.streamClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public Response get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        addHeaders(builder, headers);
        return standardClient.newCall(builder.build()).execute();
    }

    /** 同步非流式 POST，120s 读超时 */
    public Response post(String url, Map<String, String> headers, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        addHeaders(builder, headers);
        return standardClient.newCall(builder.build()).execute();
    }

    /** 流式 POST，无读超时，返回 Response 后由调用方自行读取 body 流 */
    public Response postStream(String url, Map<String, String> headers, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        addHeaders(builder, headers);
        return streamClient.newCall(builder.build()).execute();
    }

    private void addHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
    }
}
