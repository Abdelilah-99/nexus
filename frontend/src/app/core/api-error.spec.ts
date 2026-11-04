import { apiFieldErrors } from './api-error';

describe('API field errors', () => {
  it('prefers the current contract and accepts older services during rollout', () => {
    expect(apiFieldErrors({ details: { email: 'Invalid email' }, fields: { email: 'Old message' } })).toEqual({ email: 'Invalid email' });
    expect(apiFieldErrors({ details: {}, fields: { username: 'Too short' } })).toEqual({ username: 'Too short' });
    expect(apiFieldErrors({ validationErrors: { quantity: 'Must be positive' } })).toEqual({ quantity: 'Must be positive' });
  });
  it('ignores malformed payloads and non-message values', () => {
    for (const value of [undefined, null, 'failure', { details: ['bad'] }, { details: { rejected: 123 } }, {}]) {
      expect(apiFieldErrors(value)).toEqual({});
    }
  });
});
