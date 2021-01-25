package com.github.helpermethod.kafka.connect.offset.reset;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import picocli.CommandLine;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.testcontainers.utility.DockerImageName.parse;

@DisplayNameGeneration(ReplaceUnderscores.class)
@Testcontainers
class KafkaConnectOffsetResetTest {
    static final String CONNECT_OFFSETS = "connect-offsets";

    @Container
    KafkaContainer kafka =
        new KafkaContainer(parse("confluentinc/cp-kafka:6.0.1"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Test
    void should_send_tombstone_to_the_correct_partition() throws InterruptedException, ExecutionException, TimeoutException {
        createConnectOffsetsTopic();

        var producer = createProducer();
        var metadata =
            producer
                .send(new ProducerRecord<>(CONNECT_OFFSETS, "[\"jdbc-source\", {}]", "{}"))
                .get(1, SECONDS);

        new CommandLine(new KafkaConnectOffsetReset())
            .execute(
                "--bootstrap-servers", kafka.getBootstrapServers(),
                "--offsets-topic", CONNECT_OFFSETS,
                "--connector", "jdbc-source"
            );

        var consumer = createConnectOffsetsConsumer();
        var records = consumer.poll(Duration.ofSeconds(5));

        assertThat(records.records(new TopicPartition(CONNECT_OFFSETS, metadata.partition())))
            .extracting("key", "value")
            .containsExactly(
                tuple("[\"jdbc-source\", {}]", "{}"),
                tuple("[\"jdbc-source\", {}]", null)
            );
    }

    private KafkaProducer<String, String> createProducer() {
        Map<String, Object> producerConfig = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        return new KafkaProducer<>(producerConfig, new StringSerializer(), new StringSerializer());
    }

    private KafkaConsumer<String, String> createConnectOffsetsConsumer() {
        Map<String, Object> consumerConfig = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        var consumer = new KafkaConsumer<>(consumerConfig, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(CONNECT_OFFSETS));

        return consumer;
    }

    private void createConnectOffsetsTopic() throws InterruptedException, ExecutionException, TimeoutException {
        AdminClient
            .create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))
            .createTopics(List.of(new NewTopic(CONNECT_OFFSETS, 25, (short) 1)))
            .all()
            .get(5, SECONDS);
    }
}
