import type { AuthState, CoreError } from '../../core/model/models';

export interface UiErrorBanner {
  readonly title: string;
  readonly message: string;
  readonly requiresLogin?: boolean;
  readonly canRetry?: boolean;
  readonly isWarning?: boolean;
}

export function withoutStaleAuth(
  banner: UiErrorBanner | null | undefined,
  authState: AuthState,
): UiErrorBanner | null {
  if (!banner) return null;
  if (authState.kind === 'Authenticated' && banner.requiresLogin === true) {
    return null;
  }
  return banner;
}

export const CoreErrorMapper = {
  toUiError(error: CoreError): UiErrorBanner {
    switch (error.kind) {
      case 'InvalidCredentials':
        return {
          title: 'Credenciais recusadas',
          message:
            'Matrícula ou senha inválida no CAS da UnB. Confira os dados e tente novamente.',
          requiresLogin: true,
          canRetry: false,
        };

      case 'SessionExpired':
        return {
          title: 'Sessão expirada',
          message: 'Sua sessão do SIGAA expirou (40 min). Entre novamente para sincronizar.',
          requiresLogin: true,
          canRetry: false,
          isWarning: true,
        };

      case 'NoStoredCredentials':
        return {
          title: 'Conta SIGAA desconectada',
          message: 'Entre com sua matrícula e senha do SIGAA para acessar estes dados.',
          requiresLogin: true,
          canRetry: false,
          isWarning: true,
        };

      case 'NetworkUnavailable':
        return {
          title: 'Sem conexão',
          message:
            'Não foi possível conectar agora. Os dados já salvos continuam disponíveis offline.',
          requiresLogin: false,
          canRetry: true,
          isWarning: true,
        };

      case 'HttpFailure':
        return {
          title: `Portal indisponível (${error.statusCode})`,
          message:
            error.message && error.message.trim().length > 0
              ? error.message
              : `Falha ao consultar ${error.endpoint}. Tente novamente em instantes.`,
          requiresLogin: false,
          canRetry: true,
        };

      case 'ParseError':
        return {
          title: error.recoverableFallbackAvailable
            ? 'Leitura parcial do portal'
            : 'Layout alterado',
          message: error.recoverableFallbackAvailable
            ? `O layout de ${error.source} mudou; exibindo versão de contingência.`
            : `Não foi possível interpretar ${error.source}: ${error.reason}`,
          requiresLogin: false,
          canRetry: true,
          isWarning: Boolean(error.recoverableFallbackAvailable),
        };

      case 'ReadOnlyViolation':
        return {
          title: 'Bloqueio somente leitura',
          message: `Operação bloqueada por segurança (${error.blockedTarget}): ${error.reason}`,
          requiresLogin: false,
          canRetry: false,
        };

      case 'PdfExtractionError':
        return {
          title: 'Falha ao ler documento',
          message: `Não foi possível extrair ${error.documentType}: ${error.reason}`,
          requiresLogin: false,
          canRetry: true,
        };

      case 'StorageError':
        return {
          title: 'Erro de armazenamento local',
          message: `Falha em ${error.operation}: ${error.reason}`,
          requiresLogin: false,
          canRetry: true,
        };

      case 'UnavailableForProfile':
        return {
          title: `${error.feature} indisponível`,
          message: error.reason,
          requiresLogin: false,
          canRetry: false,
          isWarning: true,
        };

      case 'Unexpected':
        return {
          title: 'Erro inesperado',
          message:
            error.message.trim().length > 0
              ? error.message
              : 'Ocorreu uma falha não classificada.',
          requiresLogin: false,
          canRetry: true,
        };
    }
  },
};
