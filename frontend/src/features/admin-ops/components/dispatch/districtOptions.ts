import { SRI_LANKAN_DISTRICTS } from '@/services/api/authApi';
import { districtLabel } from '@/features/profile/profileFormat';

/** The value a system admin's district filter has when it shows every district. */
export const ALL_DISTRICTS = 'ALL';

export const DISTRICT_OPTIONS = [
  { value: ALL_DISTRICTS, label: 'All districts' },
  ...SRI_LANKAN_DISTRICTS.map((value) => ({ value, label: districtLabel(value) }))
];
