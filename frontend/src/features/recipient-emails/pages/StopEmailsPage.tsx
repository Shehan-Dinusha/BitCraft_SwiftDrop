import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MailX } from 'lucide-react';
import { Alert, Button, DensityProvider, PublicHeader, SurfaceCard } from '@/design-system';
import { notificationApi } from '@/services/api/notificationApi';
import { parseApiError } from '@/services/api/apiError';
import { useAuthStore } from '@/store/useAuthStore';

/** Reads the token from the link's fragment (#token=...), which never reaches a server log. */
function readToken(): string {
  return new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token') ?? '';
}

type Step = 'ask' | 'done' | 'error';

/**
 * Landing page of the "Stop these emails" link in a recipient's parcel email. It asks first, so a mail
 * scanner that opens every link stops nothing; only the button does.
 */
export function StopEmailsPage() {
  const navigate = useNavigate();
  const signedIn = useAuthStore((state) => Boolean(state.token));
  const [token] = useState(readToken);
  const [step, setStep] = useState<Step>(token ? 'ask' : 'error');
  const [error, setError] = useState<string>(
    token ? '' : 'This page needs the link from your email. Open the email and tap "Stop these emails" again.'
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    // Keep the token out of the address bar and browser history once it has been read.
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  const stop = async () => {
    setSaving(true);
    try {
      await notificationApi.stopRecipientEmails(token);
      setStep('done');
    } catch (requestError) {
      setError(parseApiError(requestError).message ?? 'We could not stop the emails. Check your connection and try again.');
      setStep('error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <DensityProvider density="comfortable">
      <div className="min-h-screen bg-canvas text-primary">
        <PublicHeader
          onTrack={(value) => navigate(`/track/${encodeURIComponent(value.trim())}`)}
          onSignIn={signedIn ? undefined : () => navigate('/login')}
        />
        <main className="mx-auto flex w-full max-w-xl flex-col gap-5 px-4 py-10 sm:px-6">
          <SurfaceCard>
            <div className="flex flex-col gap-4">
              <span className="flex size-12 items-center justify-center rounded-full bg-status-info-bg text-status-info-fg">
                <MailX className="size-6" aria-hidden="true" />
              </span>
              {step === 'done' ? (
                <>
                  <h1 className="text-h2 text-primary">Emails stopped</h1>
                  <p className="text-body text-secondary">
                    You will get no more emails about this parcel.
                  </p>
                </>
              ) : (
                <>
                  <h1 className="text-h2 text-primary">Stop emails about this parcel?</h1>
                  <p className="text-body text-secondary">
                    You will get no more delivery updates about it. Emails about other parcels still come.
                  </p>
                  {step === 'error' ? (
                    <Alert tone="danger" title="Emails not stopped">
                      {error}
                    </Alert>
                  ) : null}
                  {token ? (
                    <Button onClick={stop} loading={saving} loadingLabel="Stopping" className="min-h-target self-start">
                      Stop emails
                    </Button>
                  ) : null}
                </>
              )}
            </div>
          </SurfaceCard>
        </main>
      </div>
    </DensityProvider>
  );
}

export default StopEmailsPage;
