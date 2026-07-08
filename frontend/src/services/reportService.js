import api from './api';

async function downloadFile(url, filename) {
  const response = await api.get(url, { responseType: 'blob' });
  const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = blobUrl;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}

export const reportService = {
  downloadThreatsCsv: () => downloadFile('/reports/threats/csv', 'threats-report.csv'),
  downloadThreatsExcel: () => downloadFile('/reports/threats/excel', 'threats-report.xlsx'),
  downloadThreatsPdf: () => downloadFile('/reports/threats/pdf', 'threats-report.pdf'),
  downloadIncidentsCsv: () => downloadFile('/reports/incidents/csv', 'incidents-report.csv'),
  downloadIncidentsExcel: () => downloadFile('/reports/incidents/excel', 'incidents-report.xlsx'),
  downloadIncidentsPdf: () => downloadFile('/reports/incidents/pdf', 'incidents-report.pdf'),
  downloadAuditLogsCsv: () => downloadFile('/reports/audit-logs/csv', 'audit-logs-report.csv'),
  downloadAuditLogsExcel: () => downloadFile('/reports/audit-logs/excel', 'audit-logs-report.xlsx'),
  downloadSimulationsCsv: () => downloadFile('/reports/simulations/csv', 'simulation-history-report.csv'),
  downloadRiskSummaryPdf: () => downloadFile('/reports/risk-summary/pdf', 'enterprise-risk-summary.pdf'),
};
