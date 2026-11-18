export const environment = {
  production: true,
  authBypass: false,

  azureClientId: 'bcb83cb2-3d6f-4715-b3e7-204237e6f53d',
  azureTenantId: '9150f81f-b0fc-436d-998d-dcb5cf883d2e',
  azureApiScope: 'api://bcb83cb2-3d6f-4715-b3e7-204237e6f53d/write-read',
  redirectUri: 'http://localhost:4200/',

  // Puertos directos mapeados por Docker (SIN PROXY EN BACKEND)
  apiProductosUrl: 'http://localhost:18081/productos',
  apiIdentidadUrl: 'http://localhost:8082/usuarios',
  apiCarritoUrl: 'http://localhost:8083',
  apiPagosUrl: 'http://localhost:8085',
  apiMonitoreoUrl: 'http://localhost:8086',
  apiWsUrl: 'ws://localhost:8086/ws-metricas'
};
