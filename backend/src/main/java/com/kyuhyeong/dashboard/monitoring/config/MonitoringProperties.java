package com.kyuhyeong.dashboard.monitoring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    /** 화면 카드용 목록. 판정 대상 전체가 아니다 — DB 카드를 화면에 그릴 이유는 없다. */
    private List<ServiceConfig> services = new ArrayList<>();

    /** 판정 대상 컨테이너 전체. 여기 있는데 실행 중이 아니면 이상. */
    private List<String> expected = new ArrayList<>();

    /** 의도적으로 감시하지 않는 컨테이너. 실행 중이어도 unexpected 로 경고하지 않는다. */
    private List<String> ignored = new ArrayList<>();

    /**
     * <b>논리 이름 → 실제 컨테이너 후보들.</b> blue-green 무중단 배포처럼 하나의 서비스가
     * 두 컨테이너를 번갈아 쓰는 경우를 위한 매핑이다.
     *
     * <p>quiz 는 2026-09-08 무중단 배포 도입으로 {@code quiz-app} 이 사라지고
     * {@code quiz-app-blue}/{@code quiz-app-green} 두 벌이 됐다. <b>평시에 한 색만 running
     * 이고 나머지 색은 Exited 로 남는 것이 정상</b>이라, 두 이름을 그대로 {@code expected}
     * 에 넣으면 대기 색이 영구 DOWN 으로 보인다. 그래서 논리 이름 하나로 묶고
     * "멤버 중 하나라도 running 이면 UP" 으로 판정한다.
     *
     * <p>{@code services}/{@code expected} 에는 <b>논리 이름</b>을 쓴다 — 배포로 활성 색이
     * 바뀌어도 설정과 전이 로그가 흔들리지 않는다.
     */
    private Map<String, List<String>> groups = new LinkedHashMap<>();

    /**
     * 판정 주기(초). yml 키는 camelCase 그대로 — relaxed binding 이 적용되는
     * {@code @ConfigurationProperties} 경유로만 읽는다. {@code ${...}} 로 조회하지 말 것.
     * 기본값은 60 — 10초였을 때 공개 도메인 폴링이 47일간 24.4GB 아웃바운드를 만들었다.
     */
    private int checkIntervalSeconds = 60;

    /**
     * 논리 이름을 실제 컨테이너 후보 목록으로 편다.
     * 그룹이 아니면 자기 자신 하나짜리 목록 — 호출부는 그룹 여부를 신경 쓸 필요가 없다.
     */
    public List<String> membersOf(String logicalName) {
        List<String> members = groups.get(logicalName);
        return (members == null || members.isEmpty()) ? List.of(logicalName) : members;
    }

    /**
     * 감시 중인 실제 컨테이너 이름 전체(그룹은 멤버로 펼친 것).
     * unexpected 판정이 이 집합을 쓴다 — 펼치지 않으면 활성 색이 매번
     * "목록에 없는데 실행 중"으로 잘못 경고된다.
     */
    public Set<String> watchedContainerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : expected) {
            names.addAll(membersOf(name));
        }
        return names;
    }

    @Data
    public static class ServiceConfig {
        private String name;
        private String projectSlug;
        /** 논리 이름일 수 있다. 실제 컨테이너 해석은 {@link #membersOf(String)} 경유. */
        private String containerName;
    }
}
