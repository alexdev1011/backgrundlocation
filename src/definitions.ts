export interface BackgroundLocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
