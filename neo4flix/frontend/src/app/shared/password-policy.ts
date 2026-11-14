/**
 * Same rules as the user-service PasswordPolicy, so the form can show them live.
 * The server always validates again: this is only for a better user experience.
 */
export interface PasswordRule {
  label: string;
  ok: boolean;
}

export function passwordRules(password: string, username: string): PasswordRule[] {
  const p = password ?? '';
  const u = (username ?? '').trim().toLowerCase();
  return [
    { label: 'At least 10 characters', ok: p.length >= 10 && p.length <= 128 },
    { label: 'A lower-case letter', ok: /[a-z]/.test(p) },
    { label: 'An upper-case letter', ok: /[A-Z]/.test(p) },
    { label: 'A digit', ok: /\d/.test(p) },
    { label: 'A symbol (!, #, ?, ...)', ok: /[^A-Za-z0-9\s]/.test(p) },
    { label: 'No spaces', ok: p.length > 0 && !/\s/.test(p) },
    { label: 'Does not contain your username', ok: !u || !p.toLowerCase().includes(u) },
  ];
}

export function passwordScore(rules: PasswordRule[]): number {
  return rules.filter((r) => r.ok).length / rules.length;
}
