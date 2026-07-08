import api from './api';

export const threatIntelService = {
  searchCves: (query, params) => api.get('/threat-intel/cve', { params: { query, ...params } }),
  getCve: (cveId) => api.get(`/threat-intel/cve/${cveId}`),
  getIocs: (params) => api.get('/threat-intel/ioc', { params }),
  getMitreTechniques: () => api.get('/threat-intel/mitre'),
};
