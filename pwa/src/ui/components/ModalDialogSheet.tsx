import { X } from 'lucide-react';
import React, { useEffect, useId, useRef } from 'react';

interface BaseModalDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly title: string;
  readonly subtitle?: string;
  readonly headerRight?: React.ReactNode;
  readonly allowLightDismiss?: boolean;
  readonly variant?: 'sheet' | 'center' | 'fullscreen-doc';
  readonly hideHeader?: boolean;
  readonly children: React.ReactNode;
}

/**
 * Native <dialog> modal wrapper following modern-web-guidance (dialog):
 * - Opens via showModal() without role="dialog", aria-modal, or body scroll locks
 * - Uses aria-labelledby pointing to a level-1 heading (<h1>)
 * - Resets dialog.returnValue = '' in the close listener
 * - Progressive enhancement closedby="any" + pointer bounds fallback where closedBy is missing (Safari)
 */
export const ModalDialogSheet: React.FC<BaseModalDialogProps> = ({
  open,
  onClose,
  title,
  subtitle,
  headerRight,
  allowLightDismiss = true,
  variant = 'sheet',
  hideHeader = false,
  children,
}) => {
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const titleId = useId();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open) {
      if (!dialog.open) {
        dialog.showModal();
      }
    } else {
      if (dialog.open) {
        dialog.close();
      }
      return;
    }

    const handleClose = (): void => {
      dialog.returnValue = '';
      onCloseRef.current();
    };

    dialog.addEventListener('close', handleClose);

    let cleanupLightDismiss: (() => void) | undefined;
    if (
      allowLightDismiss &&
      typeof HTMLDialogElement !== 'undefined' &&
      !('closedBy' in HTMLDialogElement.prototype)
    ) {
      const outside = (e: PointerEvent): boolean => {
        const r = dialog.getBoundingClientRect();
        return (
          e.clientX < r.left ||
          e.clientX > r.right ||
          e.clientY < r.top ||
          e.clientY > r.bottom
        );
      };
      let pressedOutside = false;
      const onPointerDown = (e: PointerEvent): void => {
        pressedOutside = outside(e);
      };
      const onPointerUp = (e: PointerEvent): void => {
        if (pressedOutside && outside(e)) {
          dialog.close();
        }
        pressedOutside = false;
      };
      dialog.addEventListener('pointerdown', onPointerDown);
      dialog.addEventListener('pointerup', onPointerUp);
      cleanupLightDismiss = () => {
        dialog.removeEventListener('pointerdown', onPointerDown);
        dialog.removeEventListener('pointerup', onPointerUp);
      };
    }

    return () => {
      dialog.removeEventListener('close', handleClose);
      cleanupLightDismiss?.();
    };
  }, [open, allowLightDismiss]);

  const dialogClass =
    variant === 'center'
      ? 'unb-dialog-center'
      : variant === 'fullscreen-doc'
        ? 'unb-doc-modal'
        : 'unb-sheet';

  const dialogExtraAttrs: Record<string, string> = {
    closedby: allowLightDismiss ? 'any' : 'closerequest',
  };

  if (!open) {
    return null;
  }

  return (
    <dialog
      ref={dialogRef}
      className={dialogClass}
      aria-labelledby={hideHeader ? undefined : titleId}
      aria-label={hideHeader ? title : undefined}
      {...dialogExtraAttrs}
    >
      {variant === 'sheet' && <div className="sheet-handle" aria-hidden="true" />}

      {!hideHeader && (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: '12px',
            padding:
              variant === 'sheet'
                ? '6px 16px 4px'
                : '16px 16px 8px',
            flexShrink: 0,
          }}
        >
          <div style={{ flex: 1, minWidth: 0 }}>
            <h1
              id={titleId}
              className={variant === 'center' ? 'ty-title-strong' : 'ty-display'}
              style={{ color: 'var(--ink)' }}
            >
              {title}
            </h1>
            {subtitle && (
              <p className="ty-label" style={{ color: 'var(--muted)', marginTop: '2px' }}>
                {subtitle}
              </p>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
            {headerRight}
            <form method="dialog">
              <button
                type="submit"
                value="cancel"
                className="icon-action-btn"
                aria-label="Fechar janela"
                style={{ width: '40px', height: '40px', minWidth: '40px', minHeight: '40px' }}
              >
                <X size={19} strokeWidth={1.75} />
              </button>
            </form>
          </div>
        </div>
      )}

      <div
        className={variant === 'sheet' ? 'sheet-body' : undefined}
        style={
          variant === 'center'
            ? { padding: '8px 16px 16px', display: 'flex', flexDirection: 'column', gap: '16px' }
            : variant === 'fullscreen-doc'
              ? { flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }
              : undefined
        }
      >
        {children}
      </div>
    </dialog>
  );
};

interface ConfirmDialogProps {
  readonly open: boolean;
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
  readonly cancelLabel?: string;
  readonly isDestructive?: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel = 'Cancelar',
  isDestructive = false,
  onConfirm,
  onCancel,
}) => {
  return (
    <ModalDialogSheet
      open={open}
      onClose={onCancel}
      title={title}
      variant="center"
      allowLightDismiss
    >
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        {message}
      </p>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
        <form method="dialog">
          <button
            type="submit"
            value="cancel"
            autoFocus={isDestructive}
            className="btn-secondary"
            style={{ minHeight: '42px', padding: '8px 14px' }}
          >
            {cancelLabel}
          </button>
        </form>
        <button
          type="button"
          autoFocus={!isDestructive}
          onClick={onConfirm}
          className={isDestructive ? 'btn-danger-outline' : 'btn-primary'}
          style={{ minHeight: '42px', padding: '8px 14px' }}
        >
          {confirmLabel}
        </button>
      </div>
    </ModalDialogSheet>
  );
};
