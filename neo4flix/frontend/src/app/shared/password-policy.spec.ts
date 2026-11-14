import { passwordRules, passwordScore } from './password-policy';

describe('passwordRules', () => {
  it('accepts a strong password', () => {
    expect(passwordRules('Graph!Movies42', 'alice').every((r) => r.ok)).toBe(true);
  });

  it('flags the missing classes', () => {
    const failed = passwordRules('alllowercase', 'bob').filter((r) => !r.ok).map((r) => r.label);
    expect(failed).toContain('An upper-case letter');
    expect(failed).toContain('A digit');
    expect(failed).toContain('A symbol (!, #, ?, ...)');
  });

  it('rejects a password containing the username', () => {
    const rule = passwordRules('Alice!2024xyz', 'alice').find((r) => r.label.includes('username'));
    expect(rule?.ok).toBe(false);
  });

  it('scores between 0 and 1', () => {
    expect(passwordScore(passwordRules('', ''))).toBeLessThan(0.5);
    expect(passwordScore(passwordRules('Graph!Movies42', 'x'))).toBe(1);
  });
});
