import { useState } from 'react';
import { FileText, FileSpreadsheet, FileType2, ShieldAlert, FileWarning, ScrollText, Gauge, FlaskConical } from 'lucide-react';
import { reportService } from '../services/reportService';

const REPORT_GROUPS = [
  {
    title: 'Threat Reports',
    icon: ShieldAlert,
    reports: [
      { key: 'threatsCsv', label: 'CSV', icon: FileText, action: reportService.downloadThreatsCsv },
      { key: 'threatsExcel', label: 'Excel', icon: FileSpreadsheet, action: reportService.downloadThreatsExcel },
      { key: 'threatsPdf', label: 'PDF', icon: FileType2, action: reportService.downloadThreatsPdf },
    ],
  },
  {
    title: 'Incident Reports',
    icon: FileWarning,
    reports: [
      { key: 'incidentsCsv', label: 'CSV', icon: FileText, action: reportService.downloadIncidentsCsv },
      { key: 'incidentsExcel', label: 'Excel', icon: FileSpreadsheet, action: reportService.downloadIncidentsExcel },
      { key: 'incidentsPdf', label: 'PDF', icon: FileType2, action: reportService.downloadIncidentsPdf },
    ],
  },
  {
    title: 'Audit Logs',
    icon: ScrollText,
    reports: [
      { key: 'auditCsv', label: 'CSV', icon: FileText, action: reportService.downloadAuditLogsCsv },
      { key: 'auditExcel', label: 'Excel', icon: FileSpreadsheet, action: reportService.downloadAuditLogsExcel },
    ],
  },
  {
    title: 'Enterprise Risk Summary',
    icon: Gauge,
    reports: [
      { key: 'riskPdf', label: 'PDF', icon: FileType2, action: reportService.downloadRiskSummaryPdf },
    ],
  },
  {
    title: 'Simulation History',
    icon: FlaskConical,
    reports: [
      { key: 'simCsv', label: 'CSV', icon: FileText, action: reportService.downloadSimulationsCsv },
    ],
  },
];

export default function Reports() {
  const [busyKey, setBusyKey] = useState(null);

  const handleDownload = async (key, action) => {
    setBusyKey(key);
    try {
      await action();
    } finally {
      setBusyKey(null);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-slate-100">Reports</h2>
        <p className="text-sm text-slate-500 mt-1">
          Generate and download reports for threats, incidents, audit logs, enterprise risk, and simulation history.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-4xl">
        {REPORT_GROUPS.map((group) => (
          <div key={group.title} className="cg-card">
            <h3 className="text-sm font-semibold text-slate-300 mb-3 flex items-center gap-2">
              <group.icon size={16} className="text-cg-accent" /> {group.title}
            </h3>
            <div className="flex flex-wrap gap-2">
              {group.reports.map(({ key, label, icon: Icon, action }) => (
                <button
                  key={key}
                  disabled={busyKey === key}
                  onClick={() => handleDownload(key, action)}
                  className="cg-btn-secondary flex items-center gap-2 text-sm disabled:opacity-50"
                >
                  <Icon size={15} />
                  {busyKey === key ? 'Generating...' : label}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
