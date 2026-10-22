import { useNavigate } from 'react-router-dom';
import { Button } from '@/design-system/primitives/Button';
import { roleHome } from '@/app/roleHome';
import { useAuthStore } from '@/store/useAuthStore';

export function LoginPage() {
  const setSession = useAuthStore((state) => state.setSession);
  const navigate = useNavigate();

  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-6 text-primary">
      <section className="w-full max-w-md rounded-2xl border border-border-subtle bg-card p-8 shadow-ambient">
        <p className="text-xs font-bold uppercase tracking-wider text-brand-primary">SwiftDrop / Velocity</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Move every delivery forward.</h1>
        <p className="mt-2 text-sm text-secondary">
          Development mode signs in with a local token. Pick a persona from the bar at the top of the console.
        </p>
        <Button
          className="mt-8 w-full"
          onClick={() => {
            setSession('dev-token', 'CUSTOMER');
            navigate(roleHome.CUSTOMER, { replace: true });
          }}
        >
          Continue in development mode
        </Button>
      </section>
    </main>
  );
}

export default LoginPage;
