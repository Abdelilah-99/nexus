import { apiFieldErrors } from '../../../core/api-error';
import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { MediaService } from '../../../core/services/media';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  registerForm: FormGroup;
  readonly loading = signal<boolean>(false);
  readonly uploadingAvatar = signal<boolean>(false);
  readonly errorMessage = signal<string>('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly showPassword = signal<boolean>(false);
  readonly avatarPreview = signal<string>('');

  constructor(
    private readonly fb: FormBuilder,
    private readonly auth: AuthService,
    private readonly mediaService: MediaService,
    private readonly router: Router
  ) {
    this.registerForm = this.fb.group({
      fullName: [
        '',
        [
          Validators.required,
          Validators.minLength(2),
          Validators.maxLength(50),
          Validators.pattern(/^[\p{L}0-9_.'\- @]+$/u)
        ]
      ],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
      role: ['CLIENT', Validators.required],
      avatarUrl: ['']
    });
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  setRole(role: 'CLIENT' | 'SELLER'): void {
    this.registerForm.patchValue({ role });
  }

  // onAvatarSelect(event: Event): void {
  //   const file = (event.target as HTMLInputElement).files?.[0];
  //   if (!file) return;
  // 
  //   if (file.size > this.mediaService.MAX_FILE_SIZE) {
  //     this.errorMessage.set('Avatar image exceeds 2 MB limit.');
  //     return;
  //   }
  // 
  //   const localUrl = URL.createObjectURL(file);
  //   this.avatarPreview.set(localUrl);
  // 
  //   this.uploadingAvatar.set(true);
  //   this.errorMessage.set('');
  // 
  //   this.mediaService.uploadImage(file).subscribe({
  //     next: (res) => {
  //       this.registerForm.patchValue({ avatarUrl: res.url });
  //       this.avatarPreview.set(res.url);
  //       this.uploadingAvatar.set(false);
  //     },
  //     error: (err) => {
  //       this.errorMessage.set(err.message || 'Avatar upload failed.');
  //       this.uploadingAvatar.set(false);
  //     }
  //   });
  // }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});

    const payload = {
      ...this.registerForm.value,
      username: this.registerForm.value.fullName
    };

    this.auth.register(payload).subscribe({
      next: (res) => {
        const role = res.user?.role || this.registerForm.value.role;
        const dest = role === 'SELLER' ? '/seller/dashboard' : '/products';
        this.router.navigate([dest]);
      },
      error: (err) => {
        this.fieldErrors.set(apiFieldErrors(err.error));
        this.errorMessage.set(this.registrationError(err));
        this.loading.set(false);
      }
    });
  }

  private fieldError(fields: Record<string, string>): string {
    const messages = Object.entries(fields)
      .filter(([key, value]) => key !== 'username' && !!value)
      .map(([, value]) => value);
    if (!messages.length && fields['username']) messages.push(fields['username']);
    return [...new Set(messages)].join('. ');
}

  private registrationError(err: { error?: { fields?: Record<string, string>; details?: Record<string, string>; message?: string } | string; status?: number }): string {
    const body = err.error;
    if (typeof body === 'string' && body) return body;
    if (body && typeof body === 'object') {
      const fields = this.fieldError(apiFieldErrors(body));
      if (fields) return fields;
      if (body.message) return body.message;
    }
    if (err.status === 409) return 'Registration failed. Please try a different email.';
    if (err.status === 400) return 'Registration failed. Please check your information and try again.';
    return 'Registration failed. Please try again later.';
  }

}
