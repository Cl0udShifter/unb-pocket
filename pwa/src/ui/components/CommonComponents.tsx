import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Calendar,
  GraduationCap,
  Info,
  Utensils,
  X,
  type LucideIcon,
} from 'lucide-react';
import React, { useEffect, useState } from 'react';
import type {
  DataOrigin,
  PreloadProgress,
  UnbPocketCore,
} from '../../core/model/models';
import type { UiErrorBanner } from '../common/coreErrorMapper';
import {
  DIETARY_BADGE_LABELS,
  Formatters,
  type DietaryBadgeKind,
} from '../common/formatters';

export type TopDestination = 'RU' | 'TURMAS' | 'DISCENTE';

export const TOP_DESTINATIONS: readonly {
  readonly id: TopDestination;
  readonly label: string;
  readonly icon: LucideIcon;
}[] = [
  { id: 'RU', label: 'RU', icon: Utensils },
  { id: 'TURMAS', label: 'Turmas', icon: Calendar },
  { id: 'DISCENTE', label: 'Discente', icon: GraduationCap },
];

export type StatusTagTone = 'NEUTRAL' | 'INFO' | 'WARNING' | 'DANGER' | 'INVERTED';

export const EditorialHeader: React.FC<{
  readonly title: string;
  readonly subtitle?: React.ReactNode;
  readonly navigationIcon?: React.ReactNode;
  readonly actions?: React.ReactNode;
}> = ({ title, subtitle, navigationIcon, actions }) => (
  <header className="editorial-header">
    <div className="editorial-header__left">
      {navigationIcon}
      <div className="editorial-header__titles">
        <h1 className="ty-display">{title}</h1>
        {subtitle && <div>{subtitle}</div>}
      </div>
    </div>
    {actions && <div className="editorial-header__actions">{actions}</div>}
  </header>
);

export const BackHeaderButton: React.FC<{
  readonly onClick: () => void;
  readonly label?: string;
}> = ({ onClick, label = 'Voltar' }) => (
  <button
    type="button"
    className="back-header-btn"
    onClick={onClick}
    aria-label={label}
  >
    <ArrowLeft size={20} strokeWidth={1.75} />
  </button>
);

export const HeaderActionIconButton: React.FC<{
  readonly icon: LucideIcon;
  readonly label: string;
  readonly onClick: () => void;
  readonly showDot?: boolean;
  readonly isLoading?: boolean;
}> = ({ icon: Icon, label, onClick, showDot = false, isLoading = false }) => (
  <button
    type="button"
    className="icon-action-btn"
    onClick={onClick}
    disabled={isLoading}
    aria-label={label}
    title={label}
  >
    {isLoading ? (
      <SkeletonInlineBar width={20} height={6} color="var(--ink)" />
    ) : (
      <Icon size={21} strokeWidth={1.75} />
    )}
    {showDot && !isLoading && <span className="icon-action-btn__dot" aria-hidden="true" />}
  </button>
);

export const EditorialCard: React.FC<{
  readonly children: React.ReactNode;
  readonly onClick?: () => void;
  readonly className?: string;
  readonly style?: React.CSSProperties;
}> = ({ children, onClick, className = '', style }) => {
  if (onClick) {
    return (
      <div
        role="button"
        tabIndex={0}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        className={`editorial-card editorial-card--interactive ${className}`.trim()}
        style={style}
      >
        {children}
      </div>
    );
  }
  return (
    <div className={`editorial-card ${className}`.trim()} style={style}>
      {children}
    </div>
  );
};

export const InnerTile: React.FC<{
  readonly children: React.ReactNode;
  readonly onClick?: () => void;
  readonly bordered?: boolean;
  readonly className?: string;
  readonly style?: React.CSSProperties;
}> = ({ children, onClick, bordered = false, className = '', style }) => {
  const classes = [
    'inner-tile',
    bordered ? 'inner-tile--bordered' : '',
    onClick ? 'inner-tile--interactive' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  if (onClick) {
    return (
      <div
        role="button"
        tabIndex={0}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        className={classes}
        style={style}
      >
        {children}
      </div>
    );
  }
  return (
    <div className={classes} style={style}>
      {children}
    </div>
  );
};

export const HairlineDivider: React.FC<{ readonly style?: React.CSSProperties }> = ({ style }) => (
  <hr className="hairline-divider" style={style} />
);

export const SegmentedPillBar: React.FC<{
  readonly options: readonly string[];
  readonly selectedIndex: number;
  readonly onSelect: (index: number) => void;
}> = ({ options, selectedIndex, onSelect }) => (
  <div className="segmented-bar" role="tablist">
    {options.map((label, idx) => {
      const selected = idx === selectedIndex;
      return (
        <button
          key={label}
          type="button"
          role="tab"
          aria-selected={selected}
          className={`segmented-bar__item ${selected ? 'segmented-bar__item--active' : ''}`}
          onClick={() => onSelect(idx)}
        >
          {label}
        </button>
      );
    })}
  </div>
);

export const DietaryBadge: React.FC<{ readonly kind: DietaryBadgeKind }> = ({ kind }) => (
  <span className={`dietary-badge dietary-badge--${kind}`}>
    {DIETARY_BADGE_LABELS[kind]}
  </span>
);

export const StatusTag: React.FC<{
  readonly text: string;
  readonly tone?: StatusTagTone;
}> = ({ text, tone = 'NEUTRAL' }) => (
  <span className={`status-tag status-tag--${tone}`}>{text}</span>
);

export const OfflineOriginStrip: React.FC<{
  readonly origin: DataOrigin;
  readonly syncedAtEpochMillis: number;
  readonly extraNote?: string;
}> = ({ origin, syncedAtEpochMillis, extraNote }) => {
  const summary = Formatters.formatOriginSummary(origin, syncedAtEpochMillis);
  const text = extraNote ? `${summary} · ${extraNote}` : summary;
  const dotColor =
    origin === 'CACHE'
      ? 'var(--warning-ink)'
      : origin === 'FALLBACK'
        ? 'var(--info-ink)'
        : 'var(--muted)';

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', width: '100%' }}>
      <span
        style={{
          width: '6px',
          height: '6px',
          borderRadius: '50%',
          backgroundColor: dotColor,
          flexShrink: 0,
        }}
      />
      <span className="ty-label" style={{ color: 'var(--muted)' }}>
        {text}
      </span>
    </div>
  );
};

export const ErrorBanner: React.FC<{
  readonly banner: UiErrorBanner;
  readonly onRetry?: (() => void) | null;
  readonly onLoginClick?: (() => void) | null;
  readonly onDismiss?: (() => void) | null;
}> = ({ banner, onRetry, onLoginClick, onDismiss }) => {
  const isWarn = Boolean(banner.isWarning);
  const bg = isWarn ? 'var(--warning-bg)' : 'var(--danger-bg)';
  const fg = isWarn ? 'var(--warning-ink)' : 'var(--danger-ink)';
  const border = isWarn ? 'var(--warning-border)' : 'var(--danger-border)';
  const Icon = isWarn ? AlertTriangle : AlertCircle;

  return (
    <div
      style={{
        width: '100%',
        backgroundColor: bg,
        border: `1px solid ${border}`,
        borderRadius: 'var(--radius-card)',
        padding: '12px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1 }}>
          <Icon size={18} strokeWidth={1.75} style={{ color: fg, flexShrink: 0 }} />
          <p className="ty-title" style={{ color: fg }}>
            {banner.title}
          </p>
        </div>
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Fechar aviso"
            style={{
              width: '32px',
              height: '32px',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: fg,
            }}
          >
            <X size={16} strokeWidth={1.75} />
          </button>
        )}
      </div>

      <p className="ty-body" style={{ color: fg }}>
        {banner.message}
      </p>

      {((banner.requiresLogin && onLoginClick) || (banner.canRetry && onRetry)) && (
        <div style={{ display: 'flex', gap: '8px', marginTop: '4px', flexWrap: 'wrap' }}>
          {banner.requiresLogin && onLoginClick && (
            <button
              type="button"
              onClick={onLoginClick}
              style={{
                backgroundColor: fg,
                color: bg,
                borderRadius: 'var(--radius-tag)',
                padding: '8px 14px',
                minHeight: '38px',
              }}
              className="ty-label"
            >
              Entrar no SIGAA
            </button>
          )}
          {banner.canRetry && onRetry && (
            <button
              type="button"
              onClick={onRetry}
              style={{
                border: `1px solid ${fg}`,
                color: fg,
                borderRadius: 'var(--radius-tag)',
                padding: '8px 14px',
                minHeight: '38px',
              }}
              className="ty-label"
            >
              Tentar novamente
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export const EmptyStateCard: React.FC<{
  readonly icon: LucideIcon;
  readonly title: string;
  readonly body: string;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly secondaryLabel?: string;
  readonly onSecondaryAction?: () => void;
}> = ({
  icon: Icon,
  title,
  body,
  actionLabel,
  onAction,
  secondaryLabel,
  onSecondaryAction,
}) => (
  <EditorialCard>
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', padding: '4px 0' }}>
      <div
        style={{
          width: '40px',
          height: '40px',
          borderRadius: 'var(--radius-inner)',
          backgroundColor: 'var(--surface-raised)',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--ink)',
        }}
      >
        <Icon size={20} strokeWidth={1.75} />
      </div>
      <p className="ty-title" style={{ color: 'var(--ink)', marginTop: '12px' }}>
        {title}
      </p>
      <p className="ty-body" style={{ color: 'var(--muted)', marginTop: '4px' }}>
        {body}
      </p>
      {actionLabel && onAction && (
        <div style={{ display: 'flex', gap: '8px', marginTop: '16px', flexWrap: 'wrap' }}>
          <button type="button" className="btn-primary" onClick={onAction}>
            {actionLabel}
          </button>
          {secondaryLabel && onSecondaryAction && (
            <button type="button" className="btn-secondary" onClick={onSecondaryAction}>
              {secondaryLabel}
            </button>
          )}
        </div>
      )}
    </div>
  </EditorialCard>
);

export const SkeletonBox: React.FC<{
  readonly style?: React.CSSProperties;
  readonly className?: string;
}> = ({ style, className = '' }) => (
  <div className={`skeleton-box ${className}`.trim()} style={style} />
);

export const SkeletonInlineBar: React.FC<{
  readonly width?: number;
  readonly height?: number;
  readonly color?: string;
}> = ({ width = 44, height = 8, color }) => (
  <div
    className="skeleton-box"
    style={{
      width: `${width}px`,
      height: `${height}px`,
      borderRadius: '999px',
      backgroundColor: color ?? 'var(--surface-raised)',
    }}
  />
);

export const PreloadStatusBanner: React.FC<{
  readonly progress: PreloadProgress;
  readonly containerColor?: string;
  readonly trackColor?: string;
}> = ({ progress, containerColor, trackColor }) => {
  const isActive =
    progress.phase === 'RUNNING_ESSENTIAL' || progress.phase === 'RUNNING_BACKGROUND';
  if (!isActive) return null;

  const fraction =
    progress.totalSteps > 0
      ? Math.min(1, Math.max(0.06, progress.completedSteps / progress.totalSteps))
      : null;

  return (
    <EditorialCard
      style={{
        padding: '12px',
        backgroundColor: containerColor ?? 'var(--surface)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
        <span className="ty-overline" style={{ color: 'var(--muted)' }}>
          {progress.phase === 'RUNNING_ESSENTIAL'
            ? 'SINCRONIZANDO DADOS ESSENCIAIS'
            : 'CARREGANDO CACHE OFFLINE'}
        </span>
        {progress.totalSteps > 0 ? (
          <span className="ty-mono" style={{ color: 'var(--ink)' }}>
            {Math.min(progress.completedSteps, progress.totalSteps)}/{progress.totalSteps}
          </span>
        ) : (
          <SkeletonInlineBar width={24} height={6} color="var(--muted)" />
        )}
      </div>

      {progress.currentTarget && (
        <p
          className="ty-label"
          style={{
            color: 'var(--ink)',
            marginTop: '4px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {progress.currentTarget}
        </p>
      )}

      <div
        style={{
          width: '100%',
          height: '4px',
          borderRadius: '2px',
          backgroundColor: trackColor ?? 'var(--surface-raised)',
          marginTop: '8px',
          overflow: 'hidden',
        }}
      >
        {fraction !== null ? (
          <div
            style={{
              width: `${Math.round(fraction * 100)}%`,
              height: '100%',
              backgroundColor: 'var(--pill-active-bg)',
              borderRadius: '2px',
              transition: 'width 240ms cubic-bezier(0.4, 0, 0.2, 1)',
            }}
          />
        ) : (
          <SkeletonBox style={{ width: '100%', height: '100%', borderRadius: '2px' }} />
        )}
      </div>
    </EditorialCard>
  );
};

export const InfoCalloutRow: React.FC<{ readonly text: string }> = ({ text }) => (
  <div
    style={{
      width: '100%',
      borderRadius: 'var(--radius-inner)',
      backgroundColor: 'var(--surface-raised)',
      padding: '12px',
      display: 'flex',
      alignItems: 'flex-start',
      gap: '8px',
    }}
  >
    <Info
      size={16}
      strokeWidth={1.75}
      style={{ color: 'var(--muted)', marginTop: '2px', flexShrink: 0 }}
    />
    <p className="ty-body" style={{ color: 'var(--muted)' }}>
      {text}
    </p>
  </div>
);

export const EditorialSwitch: React.FC<{
  readonly checked: boolean;
  readonly onCheckedChange: (next: boolean) => void;
  readonly disabled?: boolean;
  readonly label?: string;
}> = ({ checked, onCheckedChange, disabled = false, label }) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    aria-label={label}
    disabled={disabled}
    className={`unb-switch ${checked ? 'unb-switch--checked' : ''}`}
    onClick={() => onCheckedChange(!checked)}
  >
    <span className="unb-switch__thumb" />
  </button>
);

export const CapsuleBottomBar: React.FC<{
  readonly currentDestination: TopDestination;
  readonly onSelect: (dest: TopDestination) => void;
}> = ({ currentDestination, onSelect }) => (
  <nav className="capsule-bottom-bar-wrap" aria-label="Navegação principal">
    <div className="capsule-bottom-bar" role="tablist">
      {TOP_DESTINATIONS.map((dest) => {
        const selected = dest.id === currentDestination;
        const Icon = dest.icon;
        return (
          <button
            key={dest.id}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-label={dest.label}
            title={dest.label}
            className={`capsule-bottom-bar__tab ${selected ? 'capsule-bottom-bar__tab--active' : ''}`}
            onClick={() => onSelect(dest.id)}
          >
            <Icon size={22} strokeWidth={1.75} />
          </button>
        );
      })}
    </div>
  </nav>
);

export const UnbNavigationRail: React.FC<{
  readonly currentDestination: TopDestination;
  readonly onSelect: (dest: TopDestination) => void;
}> = ({ currentDestination, onSelect }) => (
  <nav className="nav-rail" aria-label="Navegação lateral">
    {TOP_DESTINATIONS.map((dest) => {
      const selected = dest.id === currentDestination;
      const Icon = dest.icon;
      return (
        <button
          key={dest.id}
          type="button"
          role="tab"
          aria-selected={selected}
          aria-label={dest.label}
          className={`nav-rail__item ${selected ? 'nav-rail__item--active' : ''}`}
          onClick={() => onSelect(dest.id)}
        >
          <span className="nav-rail__pill">
            <Icon size={20} strokeWidth={1.75} />
          </span>
          <span className="ty-label">{dest.label}</span>
        </button>
      );
    })}
  </nav>
);

export const CachedBlobImage: React.FC<{
  readonly core: UnbPocketCore;
  readonly localPath: string | null | undefined;
  readonly alt: string;
  readonly style?: React.CSSProperties;
  readonly fallback?: React.ReactNode;
}> = ({ core, localPath, alt, style, fallback = null }) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
    if (!localPath || !localPath.trim()) {
      setObjectUrl(null);
      return;
    }

    let cancelled = false;
    let activeUrl: string | null = null;
    void core.readCachedBlob(localPath).then((blob) => {
      if (cancelled || !blob || blob.size === 0) {
        setObjectUrl(null);
        return;
      }
      activeUrl = URL.createObjectURL(blob);
      setObjectUrl(activeUrl);
    });

    return () => {
      cancelled = true;
      if (activeUrl) URL.revokeObjectURL(activeUrl);
    };
  }, [core, localPath]);

  if (!objectUrl || failed) {
    return <>{fallback}</>;
  }

  return (
    <img
      src={objectUrl}
      alt={alt}
      onError={() => setFailed(true)}
      style={style}
    />
  );
};

export const PortalZoomSplashOverlay: React.FC<{
  readonly onFinished: () => void;
}> = ({ onFinished }) => {
  const [phase, setPhase] = useState<'enter' | 'dip' | 'zoom' | 'done'>('enter');

  useEffect(() => {
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      const t = setTimeout(() => {
        setPhase('done');
        onFinished();
      }, 180);
      return () => clearTimeout(t);
    }

    const t1 = setTimeout(() => setPhase('dip'), 350);
    const t2 = setTimeout(() => setPhase('zoom'), 460);
    const t3 = setTimeout(() => {
      setPhase('done');
      onFinished();
    }, 720);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, [onFinished]);

  if (phase === 'done') return null;

  const scale = phase === 'enter' ? 1 : phase === 'dip' ? 0.9 : 2.8;
  const iconOpacity = phase === 'zoom' ? 0 : 1;
  const backdropOpacity = phase === 'zoom' ? 0 : 1;

  return (
    <div
      aria-hidden="true"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 100,
        backgroundColor: '#111112',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        opacity: backdropOpacity,
        transition: 'opacity 260ms cubic-bezier(0.22, 1, 0.36, 1)',
        pointerEvents: 'none',
      }}
    >
      <img
        src="/favicon.svg"
        alt=""
        style={{
          width: '128px',
          height: '128px',
          transform: `scale(${scale})`,
          opacity: iconOpacity,
          transition:
            phase === 'dip'
              ? 'transform 110ms ease-in-out'
              : 'transform 260ms cubic-bezier(0.22, 1, 0.36, 1), opacity 220ms ease',
        }}
      />
    </div>
  );
};
