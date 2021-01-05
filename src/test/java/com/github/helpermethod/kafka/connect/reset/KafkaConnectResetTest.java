package com.github.helpermethod.kafka.connect.reset;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import picocli.CommandLine;

@Testcontainers
class KafkaConnectResetTest {
    @Container
    KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.0.1"));

    @Test
    void test() {
        new CommandLine(new KafkaConnectReset()).execute("--bootstrap-servers", kafka.getBootstrapServers());
    }
}