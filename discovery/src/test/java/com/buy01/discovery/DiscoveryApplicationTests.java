package com.buy01.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.eureka.cluster.PeerEurekaNodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiscoveryApplicationTests {

    @Autowired
    private PeerEurekaNodes peers;

    @Test
    void standaloneServerDoesNotReplicateToItself() {
        assertThat(peers.isThisMyUrl("http://localhost:8761/eureka/")).isTrue();
        assertThat(peers.getPeerEurekaNodes()).isEmpty();
    }
}
