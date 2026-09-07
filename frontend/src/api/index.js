const API_BASE = '/api'

export async function fetchProjects() {
  const res = await fetch(`${API_BASE}/projects`)
  return res.json()
}

export async function fetchProject(slug) {
  const res = await fetch(`${API_BASE}/projects/${slug}`)
  return res.json()
}

export async function fetchLogs(containerName, tail = 100) {
  const res = await fetch(`${API_BASE}/monitoring/logs/${containerName}?tail=${tail}`)

  // 이 경로는 호스트 nginx 에서 차단돼 있다(현재 return 404, 관리 IP 허용 시 403).
  // 앱이 답한 실패는 text/plain(502 + 진단 문구)이고, nginx 가 답한 차단은 HTML 이다.
  // HTML 을 로그 창에 그대로 뿌리지 않고 정책 안내로 바꾼다.
  const isPlainText = (res.headers.get('content-type') || '').includes('text/plain')
  if (!res.ok && !isPlainText) {
    return [
      '🔒 보안 정책상 컨테이너 로그는 외부에 노출하지 않습니다.',
      '무인증 공개 API로 로그 전문을 반환할 수 없어, 호스트(nginx) 레벨에서 차단했습니다.',
      '(운영자는 서버에서 `docker logs <컨테이너명>` 로 직접 확인)',
    ].join('\n')
  }
  return res.text()
}
