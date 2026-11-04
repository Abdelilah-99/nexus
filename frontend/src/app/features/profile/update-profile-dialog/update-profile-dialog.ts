import { apiFieldErrors } from '../../../core/api-error';
import { Component, EventEmitter, HostListener, Input, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MediaService } from '../../../core/services/media';
import { ProfileService } from '../../../core/services/profile';
import { User } from '../../../core/models/user';

@Component({
  selector: 'app-update-profile-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './update-profile-dialog.html',
  styleUrl: './update-profile-dialog.css',
})
export class UpdateProfileDialog implements OnInit {
  @Input({ required: true }) user!: User;
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<User>();

  readonly saving = signal(false);
  readonly uploadingAvatar = signal(false);
  readonly errorMessage = signal('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly avatarPreview = signal('');
  readonly avatarMediaId = signal('');

  profileForm: FormGroup;

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly profileService: ProfileService,
    private readonly mediaService: MediaService
  ) {
    this.profileForm = this.formBuilder.group({
      username: ['', [Validators.required, Validators.minLength(2)]],
    });
  }

  ngOnInit(): void {
    this.profileForm.patchValue({
      username: this.user?.username || this.user?.fullName || '',
    });
    this.avatarPreview.set(this.user?.avatarUrl || '');
  }

  onAvatarSelect(event: Event): void {
    if (this.user?.role !== 'SELLER') {
      return;
    }

    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.errorMessage.set('');
    if (!file.type.startsWith('image/')) {
      this.errorMessage.set('Please select an image file.');
      return;
    }
    if (file.size > this.mediaService.MAX_FILE_SIZE) {
      this.errorMessage.set('Avatar image exceeds the 2 MB limit.');
      return;
    }

    this.avatarPreview.set(URL.createObjectURL(file));
    this.uploadingAvatar.set(true);
    this.mediaService.uploadImage(file).subscribe({
      next: (response) => {
        this.avatarMediaId.set(response.id);
        this.avatarPreview.set(response.url);
        this.uploadingAvatar.set(false);
      },
      error: (error) => {
        this.errorMessage.set(error.error?.message || error.message || 'Avatar upload failed.');
        this.uploadingAvatar.set(false);
      }
    });
  }

  onSubmit(): void {
    if (this.profileForm.invalid || this.uploadingAvatar()) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set('');
    this.fieldErrors.set({});
    const request: { username: string; avatarUrl?: string } = {
      username: this.profileForm.controls['username'].value || '',
    };
    if (this.user?.role === 'SELLER' && this.avatarMediaId()) {
      request.avatarUrl = this.avatarMediaId();
    }

    this.profileService.updateProfile(request).subscribe({
      next: (user) => {
        this.saving.set(false);
        this.saved.emit(user);
      },
      error: (error) => {
        let msg = '';
        this.fieldErrors.set(apiFieldErrors(error.error));
        if (Object.keys(this.fieldErrors()).length) {
          const fieldErrors = Object.values(this.fieldErrors());
          msg = Array.from(new Set(fieldErrors)).join('. ');
        }
        if (!msg && error.error?.message) {
          msg = error.error.message;
        }
        this.errorMessage.set(msg || 'Profile update failed.');
        this.saving.set(false);
      }
    });
  }

  close(): void {
    if (!this.saving() && !this.uploadingAvatar()) {
      this.closed.emit();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }
}