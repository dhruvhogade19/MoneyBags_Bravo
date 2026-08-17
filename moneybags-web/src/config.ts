export type MoneyBagsRuntimeConfig = Readonly<{
  apiBaseUrl: string;
  issuer: string;
  redirectUri: string;
  consumerClientId: string;
  adminClientId: string;
}>;

declare global {
  interface Window {
    __MONEYBAGS_CONFIG__?: Partial<MoneyBagsRuntimeConfig>;
  }
}

const runtime = window.__MONEYBAGS_CONFIG__ ?? {};

export const config: MoneyBagsRuntimeConfig = {
  apiBaseUrl: runtime.apiBaseUrl ?? "http://localhost:8080",
  issuer: runtime.issuer ?? "http://localhost:8093",
  redirectUri: runtime.redirectUri ?? "http://localhost:8000/",
  consumerClientId: runtime.consumerClientId ?? "moneybags-consumer",
  adminClientId: runtime.adminClientId ?? "moneybags-admin"
};
