/** Public error payload from the gateway and business services. */
export interface ApiError {
  code: string;
  message: string;
  details: Record<string, string>;
}

/** Prefer the shared contract while accepting responses from older deployments. */
export function apiFieldErrors(body: unknown): Record<string, string> {
  if (!body || typeof body !== 'object') return {};
  const error = body as Record<string, unknown>;
  for (const source of [error['details'], error['fields'], error['validationErrors']]) {
    if (source && typeof source === 'object' && !Array.isArray(source)) {
      const entries = Object.entries(source).filter(([, value]) => typeof value === 'string');
      if (entries.length) return Object.fromEntries(entries);
    }
  }
  return {};
}
