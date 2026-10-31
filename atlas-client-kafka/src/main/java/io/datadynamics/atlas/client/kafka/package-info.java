/**
 * Kafka 연동 기능입니다. {@link io.datadynamics.atlas.client.kafka.AtlasHookPublisher}는 엔티티 변경을
 * {@code ATLAS_HOOK} 토픽으로 보내고, {@link io.datadynamics.atlas.client.kafka.AtlasEntityChangeListener}는 Atlas가
 * {@code ATLAS_ENTITIES} 토픽에 발행하는 변경 알림을 받습니다.
 */
package io.datadynamics.atlas.client.kafka;
