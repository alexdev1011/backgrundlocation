import { WebPlugin } from '@capacitor/core';

import type { BackgroundLocationPlugin } from './definitions';

export class BackgroundLocationWeb extends WebPlugin implements BackgroundLocationPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
