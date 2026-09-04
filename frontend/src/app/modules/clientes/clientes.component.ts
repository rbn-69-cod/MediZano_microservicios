import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ClienteService } from '../../core/services/cliente.service';
import { DialogService } from '../../core/services/dialog.service';
import { Cliente } from '../../core/models/cliente.model';

@Component({
  selector: 'app-clientes',
  templateUrl: './clientes.component.html',
  styleUrls: ['./clientes.component.scss']
})
export class ClientesComponent implements OnInit {
  clientes: Cliente[] = [];
  filteredClientes: Cliente[] = [];
  isLoading = false;
  searchTerm = '';

  // Form modal state
  showModal = false;
  isEditing = false;
  editingId: number | null = null;
  clienteForm: FormGroup;
  isSubmitting = false;

  constructor(
    private clienteService: ClienteService,
    private dialogService: DialogService,
    private fb: FormBuilder
  ) {
    this.clienteForm = this.fb.group({
      tipoDocumento: ['DNI', Validators.required],
      documento: ['', [Validators.required, Validators.pattern('^[0-9]{8,11}$')]],
      nombre: ['', [Validators.required, Validators.minLength(3)]],
      telefono: [''],
      email: ['', [Validators.email]],
      direccion: [''],
      activo: [true]
    });
  }

  ngOnInit(): void {
    this.cargarClientes();
  }

  cargarClientes(): void {
    this.isLoading = true;
    this.clienteService.listarClientes(true).subscribe({
      next: (clientes) => {
        this.clientes = (clientes || []).map(c => ({
          ...c,
          activo: c.estado !== false,
          tipoDocumento: c.documento && c.documento.length === 11 ? 'RUC' : 'DNI'
        }));
        this.filtrar();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error al cargar clientes:', err);
        this.dialogService.error(err.message || 'Error al conectar con cliente-ms');
        this.isLoading = false;
      }
    });
  }

  filtrar(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) {
      this.filteredClientes = [...this.clientes];
      return;
    }
    this.filteredClientes = this.clientes.filter(c =>
      (c.nombre && c.nombre.toLowerCase().includes(term)) ||
      (c.documento && c.documento.toLowerCase().includes(term)) ||
      (c.telefono && c.telefono.toLowerCase().includes(term)) ||
      (c.email && c.email.toLowerCase().includes(term))
    );
  }

  abrirCrear(): void {
    this.isEditing = false;
    this.editingId = null;
    this.clienteForm.reset({
      tipoDocumento: 'DNI',
      documento: '',
      nombre: '',
      telefono: '',
      email: '',
      direccion: '',
      activo: true
    });
    this.showModal = true;
  }

  abrirEditar(cliente: Cliente): void {
    this.isEditing = true;
    this.editingId = cliente.id ?? null;
    this.clienteForm.patchValue({
      tipoDocumento: cliente.tipoDocumento || (cliente.documento?.length === 11 ? 'RUC' : 'DNI'),
      documento: cliente.documento || '',
      nombre: cliente.nombre,
      telefono: cliente.telefono || '',
      email: cliente.email || '',
      direccion: cliente.direccion || '',
      activo: cliente.activo !== false
    });
    this.showModal = true;
  }

  cerrarModal(): void {
    this.showModal = false;
    this.isEditing = false;
    this.editingId = null;
    this.clienteForm.reset();
  }

  guardarCliente(): void {
    if (this.clienteForm.invalid) {
      Object.keys(this.clienteForm.controls).forEach(key => {
        this.clienteForm.get(key)?.markAsTouched();
      });
      return;
    }

    this.isSubmitting = true;
    const formVal = this.clienteForm.value;
    const payload = {
      nombre: formVal.nombre,
      documento: formVal.documento,
      telefono: formVal.telefono,
      email: formVal.email,
      direccion: formVal.direccion,
      estado: formVal.activo
    };

    if (this.isEditing && this.editingId) {
      this.clienteService.actualizarCliente(this.editingId, payload).subscribe({
        next: () => {
          this.dialogService.success('Cliente actualizado correctamente');
          this.cerrarModal();
          this.isSubmitting = false;
          this.cargarClientes();
        },
        error: (err) => {
          this.dialogService.error(err.message || 'Error al actualizar cliente');
          this.isSubmitting = false;
        }
      });
    } else {
      this.clienteService.crearCliente(payload).subscribe({
        next: () => {
          this.dialogService.success('Cliente registrado exitosamente');
          this.cerrarModal();
          this.isSubmitting = false;
          this.cargarClientes();
        },
        error: (err) => {
          this.dialogService.error(err.message || 'Error al registrar cliente');
          this.isSubmitting = false;
        }
      });
    }
  }

  async eliminarCliente(cliente: Cliente): Promise<void> {
    if (!cliente.id) return;
    const confirmed = await this.dialogService.confirm(
      `¿Deseas dar de baja al cliente "${cliente.nombre}" (${cliente.documento})?`,
      'Confirmar desactivación'
    );
    if (!confirmed) return;

    this.isLoading = true;
    this.clienteService.eliminarCliente(cliente.id).subscribe({
      next: () => {
        this.dialogService.success('Cliente desactivado del directorio');
        this.cargarClientes();
      },
      error: (err) => {
        this.dialogService.error(err.message || 'Error al desactivar cliente');
        this.isLoading = false;
      }
    });
  }
}
