package com.telecom.mvno.devicedetails.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private final VendorProperties vendorProperties;

    public WebClientConfig(VendorProperties vendorProperties) {
        this.vendorProperties = vendorProperties;
    }

    @Bean
    public WebClient vendorWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, vendorProperties.getConnectTimeoutMs())
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(
                                vendorProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(vendorProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (vendorProperties.getAuthHeaderName() != null && !vendorProperties.getAuthHeaderName().isBlank()) {
            builder.defaultHeader(vendorProperties.getAuthHeaderName(), vendorProperties.getAuthHeaderValue());
        }

        return builder.build();
    }
}
