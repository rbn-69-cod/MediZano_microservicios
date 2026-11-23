import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-register',
  standalone: false,
  templateUrl: './register.component.html',
  styleUrls: ['../login/login.component.scss']
})
export class RegisterComponent {
  registerForm: FormGroup;
  errorMessage = '';
  successMessage = '';
  isLoading = false;

  constructor(private fb: FormBuilder, private authService: AuthService, private router: Router) {
    this.registerForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.maxLength(100)]],
      username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,50}$/)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.registerForm.invalid || this.registerForm.value.password !== this.registerForm.value.confirmPassword) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const { fullName, username, email, password } = this.registerForm.value;
    this.isLoading = true;
    this.errorMessage = '';
    this.authService.registerCustomer({ fullName, username, email, password }).pipe(
      finalize(() => this.isLoading = false)
    ).subscribe({
      next: () => {
        this.successMessage = 'Cuenta creada. Ya puedes iniciar sesión.';
        setTimeout(() => this.router.navigate(['/acceso/iniciar-sesion']), 900);
      },
      error: error => this.errorMessage = error?.message || 'No se pudo crear la cuenta.'
    });
  }

  get passwordsMatch(): boolean {
    const { password, confirmPassword } = this.registerForm.value;
    return !!password && password === confirmPassword;
  }
}
