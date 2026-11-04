import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should invalidate fullName when it contains invalid special characters', () => {
    const fullNameControl = component.registerForm.get('fullName');
    fullNameControl?.setValue('John Doe #$%');
    expect(fullNameControl?.valid).toBeFalsy();
    expect(fullNameControl?.hasError('pattern')).toBeTruthy();

    fullNameControl?.setValue('John Doe');
    expect(fullNameControl?.valid).toBeTruthy();
  });

  it('should display backend field validation error when registration fails with 400', () => {
    const authService = TestBed.inject(AuthService);
    const errorResponse = new HttpErrorResponse({
      status: 400,
      error: {
        status: 400,
        error: 'Validation Failed',
        fields: {
          fullName: 'Full name contains invalid characters',
          username: 'Username contains invalid characters',
        },
      },
    });
    vi.spyOn(authService, 'register').mockReturnValue(throwError(() => errorResponse));

    component.registerForm.patchValue({
      fullName: 'Valid Name',
      email: 'valid@example.com',
      password: 'password123',
      role: 'CLIENT',
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('Full name contains invalid characters');
    expect(component.loading()).toBeFalsy();
  });

  it('should display conflict error message when registration fails with 409', () => {
    const authService = TestBed.inject(AuthService);
    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: {
        status: 409,
        error: 'Conflict',
        message: 'Email already exists',
      },
    });
    vi.spyOn(authService, 'register').mockReturnValue(throwError(() => errorResponse));

    component.registerForm.patchValue({
      fullName: 'Valid Name',
      email: 'duplicate@example.com',
      password: 'password123',
      role: 'CLIENT',
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('Email already exists');
    expect(component.loading()).toBeFalsy();
  });
  it('renders shared-contract field errors inline and clears them when editing', () => {
    vi.spyOn(TestBed.inject(AuthService), 'register').mockReturnValue(throwError(() => ({ status: 400, error: {
      code: 'VALIDATION_FAILED', message: 'Invalid input', details: { fullName: 'Server rejected this name' },
      fields: { fullName: 'Old message' },
    } })));
    component.registerForm.patchValue({ fullName: 'Valid Name', email: 'valid@example.com', password: 'password123', role: 'CLIENT' });
    component.onSubmit();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#fullName-server-error').textContent).toContain('Server rejected this name');
    expect(component.errorMessage()).toBe('Server rejected this name');
    fixture.nativeElement.querySelector('#regFullName').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#fullName-server-error')).toBeNull();
  });

});
