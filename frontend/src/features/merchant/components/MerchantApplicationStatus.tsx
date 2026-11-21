import { Link } from 'react-router-dom';
import { Clock, FileText, PauseCircle, XCircle } from 'lucide-react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import type { MerchantApprovalResponse } from '@/services/api/merchantApi';

function districtName(value?: string | null) {
  return value
    ? value
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
    : 'your district';
}

/**
 * What a merchant sees before their business is approved: apply, wait for review, fix a rejected
 * application, or (if suspended) contact SwiftDrop.
 */
export function MerchantApplicationStatus({ application }: { application: MerchantApprovalResponse | null }) {
  const district = districtName(application?.operatingDistrict);

  const content = !application
    ? {
        icon: <FileText className="size-6" />,
        tone: 'bg-brand-subtle text-brand-primary',
        title: 'Tell us about your business',
        body: 'Add your business details and pickup address for approval.',
        action: { to: '/merchant/register', label: 'Add business details' }
      }
    : application.status === 'REJECTED'
      ? {
          icon: <XCircle className="size-6" />,
          tone: 'bg-status-warning-bg text-status-warning-fg',
          title: 'Your application needs changes',
          body: application.rejectionReason ?? 'Your district team could not approve the application as it stands.',
          action: { to: '/merchant/register', label: 'Edit and resubmit' }
        }
      : application.status === 'SUSPENDED'
        ? {
            icon: <PauseCircle className="size-6" />,
            tone: 'bg-status-danger-bg text-status-danger-fg',
            title: 'Your business account is suspended',
            body: 'Contact SwiftDrop support to restore access.',
            action: null
          }
        : {
            icon: <Clock className="size-6" />,
            tone: 'bg-canvas text-secondary',
            title: 'Your application is under review',
            body: `The ${district} team will email you when they decide.`,
            action: null
          };

  return (
    <main className="mx-auto max-w-2xl space-y-4 p-4 md:p-6" data-density="compact">
      <SurfaceCard className="p-6">
        <div className="flex items-start gap-4">
          <span className={`flex size-12 shrink-0 items-center justify-center rounded-xl ${content.tone}`}>
            {content.icon}
          </span>
          <div className="space-y-2">
            <h1 className="text-h2 text-primary">{content.title}</h1>
            <p className="text-sm text-secondary">{content.body}</p>
            {content.action && (
              <Link to={content.action.to} className="inline-block pt-2">
                <Button>{content.action.label}</Button>
              </Link>
            )}
          </div>
        </div>
        {application && (
          <dl className="mt-6 grid gap-3 border-t border-border-subtle pt-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs text-secondary">Business</dt>
              <dd className="font-semibold text-primary">{application.businessName}</dd>
            </div>
            <div>
              <dt className="text-xs text-secondary">Registration number</dt>
              <dd className="font-mono text-primary">{application.businessRegNo}</dd>
            </div>
            <div>
              <dt className="text-xs text-secondary">Pickup address</dt>
              <dd className="text-primary">{application.pickupAddress}</dd>
            </div>
            <div>
              <dt className="text-xs text-secondary">District</dt>
              <dd className="text-primary">{district}</dd>
            </div>
          </dl>
        )}
      </SurfaceCard>
    </main>
  );
}
