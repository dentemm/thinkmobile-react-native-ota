import { TurboModule, TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {
  getAppVersion(): Promise<string>;
  initiateUpdate(): Promise<{ success: boolean }>;
  checkForUpdate(): Promise<{ updateAvailable: boolean }>;
  restartApp(): void;
  getBundleUrl(): string;
  cleanupStorage(): Promise<{ success: boolean, deletedCount: number }>;
  setConfig(updateCheckUrl: string, apiKey: string): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('OTA');
