export const environment = {
  production: true,
  authBypass: false,

  azureClientId: 'bcb83cb2-3d6f-4715-b3e7-204237e6f53d',
  azureTenantId: '9150f81f-b0fc-436d-998d-dcb5cf883d2e',
  azureApiScope: 'api://bcb83cb2-3d6f-4715-b3e7-204237e6f53d/write-read',
  redirectUri: 'https://44.200.146.153/',

  // Despliegue Cloud Directo en EC2 (SIN PROXY / SIN API GATEWAY EN BACKEND)
  apiProductosUrl: 'http://44.200.146.153:8081/productos',
  apiIdentidadUrl: 'http://44.200.146.153:8082/usuarios',
  apiCarritoUrl: 'http://44.200.146.153:8083',
  apiPagosUrl: 'http://44.200.146.153:8085',
  apiMonitoreoUrl: 'http://44.200.146.153:8086',
  apiWsUrl: 'ws://44.200.146.153:8086/ws-metricas'
};
