import { registerPlugin } from '@capacitor/core';

import type { BackgroundLocationPlugin } from './definitions';

const BackgroundLocation = registerPlugin<BackgroundLocationPlugin>('BackgroundLocation', {
  web: () => import('./web').then(m => new m.BackgroundLocationWeb()),
});

export * from './definitions';
export { BackgroundLocation };
