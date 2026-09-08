import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

/** Independent raw Kafka observer; does not invoke the business Consumer. */
class KafkaProbe {
    public static void main(String[] args) throws Exception {
        String mode = args[0], topic = args[1];
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:29094");
        properties.put("default.api.timeout.ms", "15000");
        properties.put("request.timeout.ms", "10000");
        if (mode.equals("prepare")) {
            try (AdminClient admin = AdminClient.create(properties)) {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
            }
            return;
        }
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("enable.auto.commit", "false");
        TopicPartition partition = new TopicPartition(topic, 0);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            if (mode.equals("end")) {
                System.out.println(end);
                return;
            }
            consumer.seek(partition, 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (consumer.position(partition) < end) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("Read deadline exceeded");
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.offset() < end)
                        System.out.println(record.offset() + "\t" + record.key() + "\t" + record.value());
                }
            }
        }
    }
}
