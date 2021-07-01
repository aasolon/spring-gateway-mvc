package com.example.springgatewaymvc;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
public class ProxyController {

    // Без передачи оригинальных headers
    //
    //{
    //  "args": {},
    //  "headers": {
    //    "Accept": "application/octet-stream, application/json, application/*+json, */*",
    //    "Forwarded": "host=localhost:8080",
    //    "Host": "httpbin.org",
    //    "User-Agent": "Java/11.0.11",
    //    "X-Amzn-Trace-Id": "Root=1-60d9d8a2-349564c728a7188e51a27c70"
    //  },
    //  "origin": "77.37.206.167",
    //  "url": "http://httpbin.org/get"
    //}
    //
    //
    //
    // С передачей заголовков
    //{
    //  "args": {},
    //  "headers": {
    //    "Accept": "application/octet-stream, application/json, application/*+json, */*",
    //    "Accept-Encoding": "gzip,deflate",
    //    "Forwarded": "host=localhost:8080",
    //    "Host": "httpbin.org",
    //    "User-Agent": "Apache-HttpClient/4.5.13 (Java/11.0.11)",
    //    "X-Amzn-Trace-Id": "Root=1-60d9d8f1-5cda2acf724319a34ec85fb9",
    //    "X-Url": "http://httpbin.org"
    //  },
    //  "origin": "77.37.206.167",
    //  "url": "http://httpbin.org/get"
    //}
    //
    //
    //
    // Нормальный прокси spring-gateway на адресе localhost:8082
    // {
    //  "args": {},
    //  "headers": {
    //    "Accept": "*/*",
    //    "Accept-Encoding": "gzip,deflate",
    //    "Content-Length": "0",
    //    "Forwarded": "proto=http;host=\"localhost:8082\";for=\"127.0.0.1:52626\"",
    //    "Host": "httpbin.org",
    //    "User-Agent": "Apache-HttpClient/4.5.13 (Java/11.0.11)",
    //    "X-Amzn-Trace-Id": "Root=1-60d9e05c-105af6987632593471d3e6a8",
    //    "X-Forwarded-Host": "localhost:8082"
    //  },
    //  "origin": "127.0.0.1, 77.37.206.167",
    //  "url": "http://localhost:8082/get"
    //}


    // GET http://localhost:8080/download-file
    //
    //HTTP/1.1 200
    //Accept-Ranges: bytes
    //Date: Tue, 29 Jun 2021 14:52:49 GMT
    //Keep-Alive: timeout=60
    //Connection: keep-alive, keep-alive
    //Content-Type: application/x-msdownload
    //Content-Length: 86412328
    //
    //> 2021-06-29T175250.200.txt
    //
    //Response code: 200; Time: 725ms; Content length: 86412328 bytes
    @RequestMapping("/**")
    public ResponseEntity<?> proxy(ProxyExchange<byte[]> proxy,
                                   @RequestHeader("X-URL") String url,
                                   @RequestHeader HttpHeaders headers,
                                   HttpServletRequest request) {
        proxy.headers(headers);
        return proxy.uri(url + proxy.path()).post();
    }

//    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy11(@RequestHeader("X-URL") String url,
                                  @RequestHeader HttpHeaders headers,
                                  @RequestBody(required = false) byte[] body,
                                  HttpServletRequest request) {
        String url2 = url + request.getRequestURI();
        if (request.getQueryString() != null) {
            url2 = url2 + "?" + request.getQueryString();
        }
        URI uri = UriComponentsBuilder.fromUriString(url2).build(true).toUri();
        RequestEntity.BodyBuilder requestEntityBuilder = RequestEntity.method(HttpMethod.valueOf(request.getMethod()), uri).headers(headers);
        RequestEntity requestEntity;
        if (body != null) {
            requestEntity = requestEntityBuilder.body(body);
        } else {
            requestEntity = requestEntityBuilder.build();
        }
        ResponseEntity<byte[]> exchange = new RestTemplate().exchange(requestEntity, byte[].class);
        return exchange;
    }

    private Type type(MethodParameter parameter) {
        Type type = parameter.getGenericParameterType();
        if (type instanceof ParameterizedType) {
            ParameterizedType param = (ParameterizedType) type;
            type = param.getActualTypeArguments()[0];
        }
        return type;
    }

    // GET http://localhost:8080/download-file
    //
    //HTTP/1.1 200
    //Transfer-Encoding: chunked
    //Date: Tue, 29 Jun 2021 14:51:58 GMT
    //Keep-Alive: timeout=60
    //Connection: keep-alive
    //
    //> 2021-06-29T175159.200.txt
    //
    //Response code: 200; Time: 454ms; Content length: 86412328 bytes

//    @RequestMapping("/**")
    public ResponseEntity<?> proxy2(ProxyExchange<byte[]> proxy,
                                   @RequestHeader("X-URL") String url,
                                   @RequestHeader HttpHeaders headers,
                                   HttpServletResponse response) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<?> execute = restTemplate.execute(
                url + proxy.path(),
                HttpMethod.GET,
                null,
                clientHttpResponse -> {
//                    StreamUtils.copy(clientHttpResponse.getBody(), response.getOutputStream());
//                    IOUtils.copy(clientHttpResponse.getBody(), response.getOutputStream());
                    ResponseEntity<InputStreamResource> body = ResponseEntity
                            .status(clientHttpResponse.getRawStatusCode())
//                            .headers(clientHttpResponse.getHeaders())
                            .body(new InputStreamResource(clientHttpResponse.getBody()));
                    return body;
                });
        int i = 0;
        return execute;
    }

//    @RequestMapping("/**")
    public ResponseEntity<?> proxy3(ProxyExchange<byte[]> proxy,
                                    @RequestHeader("X-URL") String url,
                                    @RequestHeader HttpHeaders headers,
                                    HttpServletResponse response) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url + proxy.path());
        ResponseEntity<InputStreamResource> body = null;
//        try (CloseableHttpResponse response1 = client.execute(httpGet)) {
        CloseableHttpResponse response1 = client.execute(httpGet);
            final HttpEntity entity = response1.getEntity();
            if (entity != null) {
                InputStream inputStream = entity.getContent();
                HttpHeaders httpHeaders = new HttpHeaders();
                for (Header header : response1.getAllHeaders()) {
                    httpHeaders.add(header.getName(), header.getValue());
                }

//                try (InputStream inputStream = entity.getContent()) {
                    body = ResponseEntity
                            .status(response1.getStatusLine().getStatusCode())
                            .headers(httpHeaders)
//                            .header("X-File-AAAAAAA", response1.getHeaders("X-File-AAAAAAA")[0].getValue())
                            .body(new InputStreamResource(inputStream));
//                }
            }
//        }
        return body;
    }

//    @GetMapping("/**")
//    public ResponseEntity<?> proxyGet(ProxyExchange<byte[]> proxy,
//                                   @RequestHeader("X-URL") String url) {
//        return proxy.uri(url + proxy.path()).get();
//    }
//
//    @PostMapping("/**")
//    public ResponseEntity<?> proxyPost(ProxyExchange<byte[]> proxy,
//                                   @RequestHeader("X-URL") String url) {
//        return proxy.uri(url + proxy.path()).post();
//    }

    @GetMapping("/test-get")
    public String proxy() {
        return "111";
    }
}
