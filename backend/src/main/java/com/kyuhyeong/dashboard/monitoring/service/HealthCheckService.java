package com.kyuhyeong.dashboard.monitoring.service;

import com.kyuhyeong.dashboard.monitoring.config.MonitoringProperties;
import com.kyuhyeong.dashboard.monitoring.model.MonitoringInventory;
import com.kyuhyeong.dashboard.monitoring.model.ServiceStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 판정은 <b>컨테이너 상태 단일</b>이다. HTTP 폴링은 쓰지 않는다.
 * <p>공개 도메인 폴링은 nginx·TLS·라우팅·앱을 한꺼번에 통과하므로 "무엇이 죽었는지"를
 * 구분하지 못했고(판정 범위 오염), 외부 감시(UptimeRobot)와 중복이며, 감시 대상에
 * 자가 트래픽을 얹어 관찰 기준선까지 오염시켰다. HTTP 판정은 Actuator 도입 후
 * 내부 경로로 재도입한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private static final Duration DOCKER_TIMEOUT = Duration.ofSeconds(3);

    private final MonitoringProperties monitoringProperties;
    private final MonitoringDataHolder dataHolder;
    private final DockerCli docker;

    /**
     * 기동 시 목록을 한 번 검증한다. 화면 카드가 가리키는 컨테이너가 판정 대상에 없으면
     * 카드는 그려지는데 이상 감지는 안 되는 상태가 되므로 즉시 알린다.
     */
    @PostConstruct
    void validateLists() {
        List<String> expected = monitoringProperties.getExpected();
        log.info("판정 대상 {}개, 의도적 제외 {}개, 화면 카드 {}개",
                expected.size(), monitoringProperties.getIgnored().size(),
                monitoringProperties.getServices().size());
        if (expected.isEmpty()) {
            log.warn("monitoring.expected 가 비어 있다 — 양방향 비교가 무의미해진다");
        }
        monitoringProperties.getServices().stream()
                .map(MonitoringProperties.ServiceConfig::getContainerName)
                .filter(n -> !expected.contains(n))
                .forEach(n -> log.warn("화면 카드 컨테이너 {} 가 monitoring.expected 에 없다 — 카드는 뜨지만 이상 감지는 안 된다", n));

        // 그룹은 논리 이름으로만 감시한다. 멤버 이름이 expected 에 직접 들어가면
        // 대기 색(정상적으로 Exited)이 영구 DOWN 으로 잡혀 이상 판정이 상시 켜진다.
        monitoringProperties.getGroups().forEach((logical, members) -> {
            log.info("컨테이너 그룹 {} = {} (하나라도 running 이면 UP)", logical, members);
            if (!expected.contains(logical)) {
                log.warn("그룹 {} 의 논리 이름이 monitoring.expected 에 없다 — 그룹 전체가 감시에서 빠진다", logical);
            }
            members.stream().filter(expected::contains).forEach(m ->
                    log.warn("그룹 멤버 {} 가 monitoring.expected 에 직접 들어 있다 — 대기 색이 영구 DOWN 으로 보인다. 논리 이름 {} 만 남길 것", m, logical));
        });
    }

    /** 컨테이너 1건의 실측 상태. */
    public record ContainerState(String name, String dockerStatus, Instant startedAt) {}

    /**
     * 한 사이클의 docker 실측 결과.
     *
     * @param available docker 조회 자체가 성공했는가. false 면 <b>판정 불가</b>이지
     *                  "컨테이너가 죽었다"가 아니다. 이 둘을 섞으면 소켓이 한 번 삐끗할 때마다
     *                  전 컨테이너가 DOWN 으로 보이고, 뒤에 붙일 전이 판정이 알림을 쏟는다.
     * @param byName    컨테이너명 → 상태. available 이 true 인데 이름이 없으면 <b>삭제된 것</b>.
     */
    public record DockerSnapshot(boolean available, Map<String, ContainerState> byName) {}

    /**
     * @return 이번 사이클이 <b>판정 가능</b>했는가. false 면 docker 조회 자체가 실패한 것이라
     *         모든 카드는 UNKNOWN 이 되고, /health/self 는 500(판정 불가)을 답해야 한다.
     */
    public boolean checkAll() {
        DockerSnapshot snapshot = snapshot();
        for (MonitoringProperties.ServiceConfig s : monitoringProperties.getServices()) {
            dataHolder.updateServiceStatus(s.getContainerName(), toStatus(s, snapshot));
        }
        dataHolder.updateInventory(compare(snapshot));
        return snapshot.available();
    }

    /**
     * 기대 목록과 실측을 <b>양방향</b>으로 대조한다.
     * <ul>
     *   <li>목록에 있는데 실행 중이 아님 → 이상 (MISSING / DOWN)</li>
     *   <li>목록에 없는데 실행 중 → 경고 (unexpected)</li>
     * </ul>
     * 두 번째 방향이 이 비교의 핵심이다. 없으면 새 앱을 올리고 목록 갱신을 잊었을 때
     * 감시에서 조용히 빠지고, 그 사실을 알려줄 주체가 아무도 없다.
     */
    private MonitoringInventory compare(DockerSnapshot snapshot) {
        if (!snapshot.available()) {
            return MonitoringInventory.undecidable();
        }

        Map<String, String> expectedStates = new LinkedHashMap<>();
        for (String name : monitoringProperties.getExpected()) {
            // 그룹이면 멤버 중 하나라도 running 이어야 UP. 대기 색이 Exited 인 것은 이상이 아니다.
            expectedStates.put(name, resolve(name, snapshot)
                    .map(state -> "running".equals(state.dockerStatus()) ? "UP" : "DOWN")
                    .orElse("MISSING"));
        }

        // 실행 중인 것만 본다. 멈춰 있는 낯선 컨테이너까지 경고하면 잔재만으로 시끄러워진다.
        // 그룹은 멤버로 펼쳐서 비교한다 — 논리 이름만 대조하면 활성 색이 매번 unexpected 로 잡힌다.
        Set<String> watched = monitoringProperties.watchedContainerNames();
        List<String> unexpected = snapshot.byName().values().stream()
                .filter(c -> "running".equals(c.dockerStatus()))
                .map(ContainerState::name)
                .filter(n -> !watched.contains(n))
                .filter(n -> !monitoringProperties.getIgnored().contains(n))
                .sorted()
                .toList();

        return new MonitoringInventory(true, expectedStates, unexpected);
    }

    /**
     * 전 컨테이너를 열거한 뒤 상세를 조회한다.
     * <p>이름을 인자로 넘기는 {@code docker inspect <name>} 만으로는
     * "이름이 없다"와 "데몬이 죽었다"가 둘 다 빈 결과라 구분되지 않는다.
     * 열거({@code docker ps -a})는 데몬이 살아 있으면 exit 0 이므로 그 구분이 성립한다.
     */
    public DockerSnapshot snapshot() {
        DockerCli.Result listed = docker.exec(
                List.of("docker", "ps", "-a", "--format", "{{.Names}}"), DOCKER_TIMEOUT);
        if (!listed.ok()) {
            log.warn("docker 열거 실패 — 판정 불가: {}", listed.diagnostic());
            return new DockerSnapshot(false, Map.of());
        }

        List<String> names = listed.lines();
        if (names.isEmpty()) {
            return new DockerSnapshot(true, Map.of());
        }

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "inspect", "--format",
                "{{.Name}}\t{{.State.Status}}\t{{.State.StartedAt}}"));
        cmd.addAll(names);

        // 일부 이름이 그사이 사라지면 exit 1 이지만 나머지는 stdout 에 그대로 나온다.
        // 그래서 exit code 가 아니라 파싱된 행으로 판단한다.
        DockerCli.Result detail = docker.exec(cmd, DOCKER_TIMEOUT);
        Map<String, ContainerState> byName = new HashMap<>();
        for (String line : detail.lines()) {
            String[] f = line.split("\t");
            if (f.length != 3) continue;
            Instant startedAt = null;
            try {
                startedAt = Instant.parse(f[2]);
            } catch (Exception ignored) {
                // 한 번도 기동한 적 없는 컨테이너는 0001-01-01T00:00:00Z 같은 값이 온다
            }
            byName.put(f[0].replaceFirst("^/", ""), new ContainerState(f[0].replaceFirst("^/", ""), f[1], startedAt));
        }
        return new DockerSnapshot(true, byName);
    }

    /**
     * 논리 이름 하나를 <b>대표 컨테이너 1건</b>으로 접는다.
     * <ol>
     *   <li>running 인 멤버 우선. 둘 다 떠 있는 순간(배포 중 드레인 30초)은 <b>최근 기동</b>
     *       쪽을 고른다 — 워크플로가 nginx upstream 을 먼저 전환하고 구 색을 나중에 정지하므로
     *       그때 트래픽을 받는 쪽은 새 색이다. (활성 색의 진짜 근거는 nginx upstream 파일인데
     *       그건 다른 컨테이너의 파일이라 여기서 읽을 수 없다 — 기동 시각이 최선의 근사다.)</li>
     *   <li>running 이 없으면 존재하는 멤버 중 최근 기동 쪽 — 상태는 DOWN 이 된다.</li>
     *   <li>멤버가 하나도 없으면 empty — MISSING 이다.</li>
     * </ol>
     */
    private Optional<ContainerState> resolve(String logicalName, DockerSnapshot snapshot) {
        return monitoringProperties.membersOf(logicalName).stream()
                .map(snapshot.byName()::get)
                .filter(java.util.Objects::nonNull)
                .max(Comparator
                        .comparing((ContainerState c) -> "running".equals(c.dockerStatus()))
                        .thenComparing(c -> c.startedAt() == null ? Instant.EPOCH : c.startedAt()));
    }

    private ServiceStatus toStatus(MonitoringProperties.ServiceConfig service, DockerSnapshot snapshot) {
        String status;
        String dockerStatus;
        long uptimeSeconds = 0;
        // 로그 조회는 실제 컨테이너 이름이라야 통한다. 논리 이름(quiz-app)으로는 docker logs 가 실패한다.
        String actualName = service.getContainerName();

        if (!snapshot.available()) {
            status = "UNKNOWN";          // 판정 불가. UP 으로도 DOWN 으로도 위장하지 않는다
            dockerStatus = "unknown";
        } else {
            ContainerState state = resolve(service.getContainerName(), snapshot).orElse(null);
            if (state == null) {
                status = "MISSING";      // 조회는 됐는데 없다 = 컨테이너가 삭제됐다
                dockerStatus = "none";
            } else {
                actualName = state.name();
                dockerStatus = state.dockerStatus();
                status = "running".equals(dockerStatus) ? "UP" : "DOWN";
                if (state.startedAt() != null) {
                    uptimeSeconds = Duration.between(state.startedAt(), Instant.now()).getSeconds();
                    if (uptimeSeconds < 0) uptimeSeconds = 0;
                }
            }
        }

        return ServiceStatus.builder()
                .name(service.getName())
                .projectSlug(service.getProjectSlug())
                .containerName(actualName)
                .status(status)
                .dockerStatus(dockerStatus)
                .uptimeSeconds(uptimeSeconds)
                .checkedAt(LocalDateTime.now())
                .build();
    }

}
