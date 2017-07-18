package org.skywalking.apm.agent.core.remote;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.test.tools.AgentServiceRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DiscoveryRestServiceClientTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private DiscoveryRestServiceClient client;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Before
    public void setUpBeforeClass() {
        Config.Collector.DISCOVERY_CHECK_INTERVAL = 1;
        stubFor(get(urlEqualTo("/withoutResult"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));
        stubFor(get(urlEqualTo("/withResult"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("['127.0.0.1:8080','127.0.0.1:8090']")));
        stubFor(get(urlEqualTo("/withSameResult"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("['127.0.0.1:8090','127.0.0.1:8080']")));
        stubFor(get(urlEqualTo("/withDifferenceResult"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("['127.0.0.1:9090','127.0.0.1:18090']")));
        stubFor(get(urlEqualTo("/with404"))
            .willReturn(aResponse()
                .withStatus(400)));
    }

    @Test
    public void testWithoutCollectorServer() throws RESTResponseStatusError, IOException {
        client = new DiscoveryRestServiceClient();
        client.run();
        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.size(), is(0));
    }

    @Test
    public void testWithGRPCAddress() throws RESTResponseStatusError, IOException {
        Config.Collector.SERVERS = "127.0.0.1:8089";
        Config.Collector.DISCOVERY_SERVICE_NAME = "/withResult";
        client = new DiscoveryRestServiceClient();
        client.run();

        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.size(), is(2));
        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.contains("127.0.0.1:8080"), is(true));
        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.contains("127.0.0.1:8090"), is(true));
    }

    @Test
    public void testWithoutGRPCAddress() throws RESTResponseStatusError, IOException {
        Config.Collector.SERVERS = "127.0.0.1:8089";
        Config.Collector.DISCOVERY_SERVICE_NAME = "/withoutResult";
        client = new DiscoveryRestServiceClient();
        client.run();

        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.size(), is(0));
    }

    @Test
    public void testChangeGrpcAddress() throws RESTResponseStatusError, IOException {
        Config.Collector.SERVERS = "127.0.0.1:8089";
        Config.Collector.DISCOVERY_SERVICE_NAME = "/withResult";
        client = new DiscoveryRestServiceClient();
        client.run();

        Config.Collector.DISCOVERY_SERVICE_NAME = "/withDifferenceResult";
        client.run();

        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.size(), is(2));
        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.contains("127.0.0.1:9090"), is(true));
        assertThat(RemoteDownstreamConfig.Collector.GRPC_SERVERS.contains("127.0.0.1:18090"), is(true));
    }

    @After
    public void tearDown() {
        Config.Collector.SERVERS = "";
        Config.Collector.DISCOVERY_SERVICE_NAME = "/grpc/address";
        RemoteDownstreamConfig.Collector.GRPC_SERVERS.clear();
    }

}