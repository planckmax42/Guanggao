package com.example.adplatform.search.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * ES 管理 API 的轻量 REST 封装。
 *
 * <p>Spring Data Elasticsearch 负责文档读写；索引模板、ILM 和原子别名操作没有合适的
 * 高层抽象，因此集中在此处调用低层 RestClient，避免业务服务重复处理 JSON 和 HTTP。</p>
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchRestSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** 对管理端点执行 HEAD 存在性检查。 */
    public boolean exists(String endpoint) throws IOException {
        Response response = restClient.performRequest(new Request("HEAD", endpoint));
        return response.getStatusLine().getStatusCode() == 200;
    }

    /** 读取 JSON 对象形式的管理 API 响应。 */
    public Map<String, Object> get(String endpoint) throws IOException {
        Response response = restClient.performRequest(new Request("GET", endpoint));
        return objectMapper.readValue(response.getEntity().getContent(), MAP_TYPE);
    }

    public void put(String endpoint, Object body) throws IOException {
        perform("PUT", endpoint, body);
    }

    public void post(String endpoint, Object body) throws IOException {
        perform("POST", endpoint, body);
    }

    private void perform(String method, String endpoint, Object body) throws IOException {
        Request request = new Request(method, endpoint);
        if (body != null) {
            request.setEntity(new NStringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
        }
        restClient.performRequest(request);
    }
}
