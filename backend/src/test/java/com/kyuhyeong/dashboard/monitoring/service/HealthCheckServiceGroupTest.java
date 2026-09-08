package com.kyuhyeong.dashboard.monitoring.service;

import com.kyuhyeong.dashboard.monitoring.config.MonitoringProperties;
import com.kyuhyeong.dashboard.monitoring.model.MonitoringData;
import com.kyuhyeong.dashboard.monitoring.model.ServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * blue-green 무중단 배포(2026-09-08) 대응 — 논리 이름 {@code quiz-app} 이
 * 실제 컨테이너 {@code quiz-app-blue}/{@code quiz-app-green} 중 활성 색으로 접히는지 본다.
 *
 * <p>이 판정이 깨지면 화면은 MISSING/none 으로 보이는데 서비스는 멀쩡한 상태가 된다
 * (실제로 그렇게 됐다 — 이름만 바뀌었을 뿐인데 감시가 대상을 잃었다).
 */
class HealthCheckServiceGroupTest {

    /** 지정한 이름들을 그대로 되돌려 주는 가짜 docker. 형식은 실제 CLI 출력과 같다. */
    private static class FakeDocker extends DockerCli {
        private final List<String[]> containers;   // {이름, 상태, StartedAt}
        private final boolean daemonUp;

        FakeDocker(boolean daemonUp, List<String[]> containers) {
            this.daemonUp = daemonUp;
            this.containers = containers;
        }

        @Override
        public Result exec(List<String> command, Duration timeout) {
            if (!daemonUp) {
                return new Result(true, false, 1, "", "Cannot connect to the Docker daemon");
            }
            if (command.contains("inspect")) {
                String out = containers.stream()
                        .map(c -> "/" + c[0] + "\t" + c[1] + "\t" + c[2])
                        .reduce("", (a, b) -> a + b + "\n");
                return new Result(true, false, 0, out, "");
            }
            String out = containers.stream().map(c -> c[0]).reduce("", (a, b) -> a + b + "\n");
            return new Result(true, false, 0, out, "");
        }
    }

    private MonitoringProperties props;

    @BeforeEach
    void setUp() {
        props = new MonitoringProperties();

        MonitoringProperties.ServiceConfig quiz = new MonitoringProperties.ServiceConfig();
        quiz.setName("Song Quiz");
        quiz.setProjectSlug("song-quiz");
        quiz.setContainerName("quiz-app");          // 논리 이름
        props.setServices(List.of(quiz));

        props.setExpected(List.of("quiz-app", "quiz-db"));
        props.setGroups(Map.of("quiz-app", List.of("quiz-app-blue", "quiz-app-green")));
    }

    private MonitoringData run(DockerCli docker) {
        MonitoringDataHolder holder = new MonitoringDataHolder(props);
        HealthCheckService service = new HealthCheckService(props, holder, docker);
        service.checkAll();
        return holder.getAll();
    }

    @Test
    void 대기_색이_Exited_여도_활성_색이_running_이면_UP() {
        // 운영 실측(2026-09-08): green 이 활성, blue 는 배포로 정지된 대기 색
        DockerCli docker = new FakeDocker(true, List.<String[]>of(
                new String[]{"quiz-app-green", "running", "2026-09-08T10:00:00Z"},
                new String[]{"quiz-app-blue", "exited", "2026-09-08T09:00:00Z"},
                new String[]{"quiz-db", "running", "2026-09-07T22:00:00Z"}));

        MonitoringDataHolder holder = new MonitoringDataHolder(props);
        new HealthCheckService(props, holder, docker).checkAll();

        ServiceStatus quiz = holder.getAll().services().get(0);
        assertThat(quiz.getStatus()).isEqualTo("UP");
        assertThat(quiz.getDockerStatus()).isEqualTo("running");
        // 로그 조회는 실제 이름이라야 통한다 — 논리 이름을 그대로 내보내면 docker logs 가 실패한다
        assertThat(quiz.getContainerName()).isEqualTo("quiz-app-green");

        assertThat(holder.getInventory().expectedStates()).containsEntry("quiz-app", "UP");
        // 대기 색은 expected 에 없지만 그룹 멤버라 unexpected 경고 대상이 아니다
        assertThat(holder.getInventory().unexpected()).isEmpty();
        assertThat(holder.getInventory().hasAnomaly()).isFalse();
    }

    @Test
    void 드레인_중_두_색이_모두_떠_있으면_최근_기동_쪽을_고른다() {
        // 배포 5단계에서 nginx 는 이미 새 색으로 전환됐고 구 색은 30초 뒤 정지된다
        DockerCli docker = new FakeDocker(true, List.<String[]>of(
                new String[]{"quiz-app-blue", "running", "2026-09-08T09:00:00Z"},
                new String[]{"quiz-app-green", "running", "2026-09-08T10:00:00Z"},
                new String[]{"quiz-db", "running", "2026-09-07T22:00:00Z"}));

        assertThat(run(docker).services().get(0).getContainerName()).isEqualTo("quiz-app-green");
    }

    @Test
    void 두_색_모두_멈춰_있으면_DOWN() {
        DockerCli docker = new FakeDocker(true, List.<String[]>of(
                new String[]{"quiz-app-blue", "exited", "2026-09-08T09:00:00Z"},
                new String[]{"quiz-app-green", "exited", "2026-09-08T10:00:00Z"},
                new String[]{"quiz-db", "running", "2026-09-07T22:00:00Z"}));

        MonitoringDataHolder holder = new MonitoringDataHolder(props);
        new HealthCheckService(props, holder, docker).checkAll();

        assertThat(holder.getAll().services().get(0).getStatus()).isEqualTo("DOWN");
        assertThat(holder.getInventory().expectedStates()).containsEntry("quiz-app", "DOWN");
    }

    @Test
    void 멤버가_하나도_없으면_MISSING() {
        DockerCli docker = new FakeDocker(true, List.<String[]>of(
                new String[]{"quiz-db", "running", "2026-09-07T22:00:00Z"}));

        MonitoringDataHolder holder = new MonitoringDataHolder(props);
        new HealthCheckService(props, holder, docker).checkAll();

        ServiceStatus quiz = holder.getAll().services().get(0);
        assertThat(quiz.getStatus()).isEqualTo("MISSING");
        assertThat(quiz.getDockerStatus()).isEqualTo("none");
        assertThat(holder.getInventory().expectedStates()).containsEntry("quiz-app", "MISSING");
    }

    @Test
    void docker_조회_실패는_MISSING_이_아니라_UNKNOWN() {
        // MISSING(컨테이너 소멸)과 UNKNOWN(판정 불가)을 섞으면 소켓이 한 번 삐끗할 때마다
        // 전 컨테이너가 죽은 것처럼 보인다
        MonitoringDataHolder holder = new MonitoringDataHolder(props);
        boolean decidable = new HealthCheckService(props, holder, new FakeDocker(false, List.<String[]>of())).checkAll();

        assertThat(decidable).isFalse();
        assertThat(holder.getAll().services().get(0).getStatus()).isEqualTo("UNKNOWN");
    }
}
