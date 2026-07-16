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

@Component
@RequiredArgsConstructor
public class ElasticsearchRestSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public boolean exists(String endpoint) throws IOException {
        Response response = restClient.performRequest(new Request("HEAD", endpoint));
        return response.getStatusLine().getStatusCode() == 200;
    }

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
