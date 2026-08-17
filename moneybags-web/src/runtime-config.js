// Override these values when the frontend is deployed away from the local stack.
window.__MONEYBAGS_CONFIG__ = Object.assign({
  apiBaseUrl: "http://localhost:8080",
  issuer: "http://localhost:8093",
  redirectUri: "http://localhost:8000/",
  consumerClientId: "moneybags-consumer",
  adminClientId: "moneybags-admin"
}, window.__MONEYBAGS_CONFIG__ || {});
