package io.datadynamics.atlas.client.kafka;

import io.datadynamics.atlas.client.kafka.message.HookMessageType;

/**
 * hook 메시지가 기록된 위치입니다. Kafka가 메시지를 받아들였다는 뜻일 뿐이며, Atlas는 이를 비동기로 처리합니다.
 *
 * @param type      보낸 hook 메시지 종류
 * @param topic     메시지를 기록한 토픽
 * @param partition 메시지를 기록한 파티션(나뉜 메시지라면 마지막 조각의 파티션)
 * @param offset    마지막 조각의 오프셋
 * @param parts     사용한 Kafka 메시지 수(메시지가 나뉘었으면 1보다 큼)
 */
public record PublishResult(HookMessageType type, String topic, int partition, long offset, int parts) {
}
