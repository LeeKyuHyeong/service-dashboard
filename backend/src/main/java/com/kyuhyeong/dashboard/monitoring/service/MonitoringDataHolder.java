package com.kyuhyeong.dashboard.monitoring.service;

import com.kyuhyeong.dashboard.monitoring.config.MonitoringProperties;
import com.kyuhyeong.dashboard.monitoring.model.MonitoringData;
import com.kyuhyeong.dashboard.monitoring.model.MonitoringInventory;
import com.kyuhyeong.dashboard.monitoring.model.ServerMetric;
import com.kyuhyeong.dashboard.monitoring.model.ServiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MonitoringDataHolder {

    private final MonitoringProperties properties;

    private final ConcurrentHashMap<String, ServiceStatus> serviceStatuses = new ConcurrentHashMap<>();
    private volatile ServerMetric serverMetric;

    /**
     * 마지막으로 판정 사이클이 <b>성공</b>한 시각. null 이면 기동 후 한 번도 완주하지 못한 것.
     * <p>기동 시각으로 초기화하지 않는다 — 그렇게 하면 루프가 한 번도 돌지 않아도
     * 최초 (checkIntervalSeconds x 3)초 동안 200 을 주게 되고, 배포 게이트가
     * "컨텍스트만 뜨면 초록"이 된다(거짓 초록).
     */
    private volatile Instant lastCheckedAt;

    /** 직전 사이클이 예외로 끝난 사유. null 이면 정상. 사이클이 성공하면 지워진다. */
    private volatile String lastFailureReason;

    public void updateServiceStatus(String containerName, ServiceStatus status) {
        serviceStatuses.put(containerName, status);
    }

    public void updateServerMetric(ServerMetric metric) {
        this.serverMetric = metric;
    }

    public MonitoringData getAll() {
        // ConcurrentHashMap.values() 는 해시 순서라 카드 순서가 뒤죽박죽 →
        // yml(monitoring.services) 정의 순서대로 방출해 순서를 고정한다.
        List<ServiceStatus> services = properties.getServices().stream()
                .map(s -> serviceStatuses.get(s.getContainerName()))
                .filter(Objects::nonNull)
                .toList();
        return new MonitoringData(services, serverMetric, LocalDateTime.now());
    }

    /** 직전 사이클의 양방향 비교 결과. 전이 판정(다음 단계)이 이 값을 이전 값과 비교한다. */
    private volatile MonitoringInventory inventory = MonitoringInventory.undecidable();

    public void updateInventory(MonitoringInventory inventory) {
        this.inventory = inventory;
    }

    public MonitoringInventory getInventory() {
        return this.inventory;
    }

    public void markChecked() {
        this.lastCheckedAt = Instant.now();
        this.lastFailureReason = null;
    }

    public void markFailed(String reason) {
        this.lastFailureReason = reason;
    }

    /**
     * 사이클은 끝까지 돌았지만 판정 근거를 얻지 못한 경우(docker 조회 실패 등).
     * lastCheckedAt 을 <b>갱신하지 않는다</b> — 돌긴 돌았으니 200 을 주면
     * "판정 불가"가 "정상"으로 위장된다.
     */
    public void markUndecidable(String reason) {
        this.lastFailureReason = reason;
    }

    /** @return 마지막 성공 사이클로부터 경과 초. 아직 한 번도 완주하지 못했으면 null. */
    public Long getLastCheckedAgeSeconds() {
        Instant at = this.lastCheckedAt;
        return at == null ? null : Duration.between(at, Instant.now()).getSeconds();
    }

    public String getLastFailureReason() {
        return this.lastFailureReason;
    }
}
