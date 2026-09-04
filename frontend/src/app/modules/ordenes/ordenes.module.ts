import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrdenesComponent } from './ordenes.component';
import { OrdenesRoutingModule } from './ordenes-routing.module';
import { SvgIconComponent } from '../../core/components/svg-icon/svg-icon.component';

@NgModule({
  declarations: [OrdenesComponent],
  imports: [
    CommonModule,
    FormsModule,
    OrdenesRoutingModule,
    SvgIconComponent
  ]
})
export class OrdenesModule { }
