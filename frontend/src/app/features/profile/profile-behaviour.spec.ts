import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Profile } from './profile';
import { ProfileService } from '../../core/services/profile';
import { AuthService } from '../../core/services/auth';
import { User } from '../../core/models/user';

const user: User = { id: '1', email: 'a@b.c', username: 'ada', role: 'CLIENT' };

describe('Profile behaviour', () => {
  let component: Profile;
  let profileService: any;
  let auth: any;

  beforeEach(() => {
    profileService = { getProfile: vi.fn(() => of(user)) };
    auth = { updateCurrentUser: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: ProfileService, useValue: profileService },
        { provide: AuthService, useValue: auth },
      ],
    });
    component = TestBed.runInInjectionContext(
      () => new Profile(TestBed.inject(ProfileService), TestBed.inject(AuthService)));
  });

  it('loads the profile and updates auth', () => {
    component.ngOnInit();
    expect(component.profile()).toEqual(user);
    expect(auth.updateCurrentUser).toHaveBeenCalledWith(user);
    expect(component.loading()).toBe(false);
  });

  it('reports a load error', () => {
    profileService.getProfile.mockReturnValue(throwError(() => ({ error: { message: 'nope' } })));
    component.ngOnInit();
    expect(component.errorMessage()).toBe('nope');
    expect(component.loading()).toBe(false);
  });

  it('uses a default error message', () => {
    profileService.getProfile.mockReturnValue(throwError(() => ({})));
    component.ngOnInit();
    expect(component.errorMessage()).toContain('Unable to load');
  });

  it('opens and closes the edit dialog', () => {
    component.openEditDialog();
    expect(component.dialogOpen()).toBe(true);
    component.closeEditDialog();
    expect(component.dialogOpen()).toBe(false);
  });

  it('handles a saved profile', () => {
    vi.useFakeTimers();
    component.onProfileSaved(user);
    expect(component.profile()).toEqual(user);
    expect(auth.updateCurrentUser).toHaveBeenCalledWith(user);
    expect(component.dialogOpen()).toBe(false);
    expect(component.toastMessage()).toContain('updated');
    vi.advanceTimersByTime(3600);
    expect(component.toastMessage()).toBe('');
    vi.useRealTimers();
  });

  it('derives display names', () => {
    expect(component.getDisplayName({ ...user, fullName: 'Ada L' })).toBe('Ada L');
    expect(component.getDisplayName(user)).toBe('ada');
    expect(component.getDisplayName({ ...user, username: undefined })).toBe('BUY02 member');
  });

  it('derives initials', () => {
    expect(component.getInitials({ ...user, fullName: 'Ada Lovelace' })).toBe('AL');
    expect(component.getInitials({ ...user, username: 'neo' })).toBe('NE');
  });
});