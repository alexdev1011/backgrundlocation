

export interface WatcherOptions {
  notificationTitle: String;
  notificationContent: String;
  minS: number;
  urlRequests: String | null;
  inBG: boolean;
  distanceFilter: number;
  userId : string;
  authorization : string | null;
  imei : string;
}

export interface Location {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number | null;
  altitudeAccuracy: number | null;
  simulated: boolean;
  bearing: number | null;
  speed: number | null;
  time: number | null;
}

export interface BackgroundLocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;

  panic() : void;

  status(callback: (status: boolean) => void): void;

  getBitacora( callback : ( bitacora : any[]) => void ) : void;

  getTramas(callback: (tramas: any) => void): void;

  clearTramas(callback: ( ) => void): void;

  startService(options: WatcherOptions,
    callback: (
      position?: Location,
      error?: Object
    ) => void
  ): Promise<string>;

  stopService(): Promise<string>;
}
