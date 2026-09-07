import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { formatUptime } from '../utils/format';
import LogModal from './LogModal';

export default function ProjectCard({ project, service }) {
  const navigate = useNavigate();
  const [logOpen, setLogOpen] = useState(false);

  // 판정은 컨테이너 상태 단일. UNKNOWN(판정 불가)을 UP/DOWN 어느 쪽으로도 칠하지 않는다.
  const badgeClass =
    service?.status === 'UP' ? 'badge--up'
    : service?.status === 'UNKNOWN' ? 'badge--unknown'
    : 'badge--down';

  return (
    <>
      <div className="card project-card">
        <div className="project-card__thumbnail">
          {project.thumbnailUrl ? (
            <img src={project.thumbnailUrl} alt={project.name} />
          ) : (
            <div className="project-card__placeholder">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" opacity="0.3">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
                <line x1="8" y1="21" x2="16" y2="21" />
                <line x1="12" y1="17" x2="12" y2="21" />
              </svg>
            </div>
          )}
        </div>

        <div className="project-card__body">
          <div className="project-card__header">
            <h3 className="project-card__name">{project.name}</h3>
            {service && (
              <span className={`badge ${badgeClass}`}>{service.status}</span>
            )}
          </div>

          <p className="project-card__desc">{project.description}</p>

          {project.techStack && project.techStack.length > 0 && (
            <div className="tag-list">
              {project.techStack.map((tech) => (
                <span key={tech} className="tag">{tech}</span>
              ))}
            </div>
          )}

          {service && (
            <div className="service-card__metrics">
              <div className="service-card__row">
                <span className="service-card__label">Docker</span>
                <span className={`service-card__value ${
                  service.dockerStatus === 'running' ? 'text-green'
                  : service.dockerStatus === 'unknown' ? ''
                  : 'text-red'}`}>
                  {service.dockerStatus || '—'}
                </span>
              </div>
              <div className="service-card__row">
                <span className="service-card__label">가동 시간</span>
                <span className="service-card__value">
                  {formatUptime(service.uptimeSeconds)}
                </span>
              </div>
            </div>
          )}

          <div className="project-card__actions">
            {service && (
              <button className="btn btn--secondary" onClick={() => setLogOpen(true)}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
                </svg>
                로그
              </button>
            )}
            <button className="btn btn--primary" onClick={() => navigate(`/projects/${project.slug}`)}>
              상세 &rarr;
            </button>
          </div>
        </div>
      </div>

      {service && (
        <LogModal
          isOpen={logOpen}
          onClose={() => setLogOpen(false)}
          containerName={service.containerName}
          serviceName={service.name}
        />
      )}
    </>
  );
}
