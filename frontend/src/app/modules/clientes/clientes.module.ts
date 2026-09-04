import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ClientesComponent } from './clientes.component';
import { ClientesRoutingModule } from './clientes-routing.module';
import { SvgIconComponent } from '../../core/components/svg-icon/svg-icon.component';

@NgModule({
  declarations: [ClientesComponent],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    ClientesRoutingModule,
    SvgIconComponent
  ]
})
export class ClientesModule { }
