package io.datadynamics.atlas.client.kafka;

import io.datadynamics.atlas.client.kafka.message.HookMessageType;

/**
 * Where a hook message was written. Kafka accepted it; Atlas processes it asynchronously.
 *
 * @param offset offset of the last part
 * @param parts  number of Kafka messages used (more than one for split messages)
 */
public record PublishResult(HookMessageType type, String topic, int partition, long offset, int parts) {
}
