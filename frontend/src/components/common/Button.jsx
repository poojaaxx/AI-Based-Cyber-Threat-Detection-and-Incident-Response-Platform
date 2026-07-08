import { Loader2 } from 'lucide-react';

const VARIANT_CLASS = {
  primary: 'cg-btn-primary',
  secondary: 'cg-btn-secondary',
  success: 'cg-btn-success',
  warning: 'cg-btn-warning',
  danger: 'cg-btn-danger',
  outline: 'cg-btn-outline',
  ghost: 'cg-btn-ghost',
};

/**
 * Reusable button with the app's 7 standard variants (primary/secondary/success/
 * warning/danger/outline/ghost), a built-in loading spinner, and consistent
 * disabled styling. Composes the existing cg-btn-* utility classes rather than
 * introducing a parallel styling system.
 */
export default function Button({
  variant = 'primary', loading = false, disabled = false, icon: Icon, children, className = '', ...props
}) {
  return (
    <button
      disabled={disabled || loading}
      className={`${VARIANT_CLASS[variant] || VARIANT_CLASS.primary} inline-flex items-center justify-center gap-2 ${className}`}
      {...props}
    >
      {loading ? <Loader2 size={16} className="animate-spin" /> : Icon && <Icon size={16} />}
      {children}
    </button>
  );
}
