import { WebPlugin } from '@capacitor/core';

import type { BackgroundLocationPlugin, Location,WatcherOptions } from './definitions';

export class BackgroundLocationWeb extends WebPlugin implements BackgroundLocationPlugin {

  setMotivo( motivo : { code : number, message : string } ){
    console.log(motivo);
    
  }

  getBitacora(callback: (bitacora: any[]) => void): void {
    callback([]);
  }
  
  panic(): void {
    
  }
  clearTramas(callback: () => void): void {
    callback();
  }
  getTramas(callback: (tramas: any) => void): void {
    callback( { tramas : [] });
  }
  status(callback: ( status : boolean ) => void): void {
    callback( false );
  }
  startService(options: WatcherOptions, callback: (position?: Location | undefined, error?: Object | undefined) => void): Promise<string> {
    return new Promise( resolve => {
      if(options.notificationContent){
        callback( undefined );
      }
      resolve("eso esta de adorno ")
    })
  }

  stopService(): Promise<string> {
    throw new Error('Method not implemented.');
  }
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
