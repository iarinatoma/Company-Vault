import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';

/** Native `<dialog>` confirmation: the title is an input, the body is projected content. */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  readonly heading = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly cancelLabel = input.required<string>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);

  readonly confirmed = output<void>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (!el.open) el.showModal();
    });
  }
}
