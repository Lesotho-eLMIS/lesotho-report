/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.report.dto;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openlmis.report.domain.DashboardReport;
import org.openlmis.report.domain.DashboardReport.Exporter;
import org.openlmis.report.domain.DashboardReport.Importer;
import org.openlmis.report.domain.ReportCategory;

import org.openlmis.report.utils.ReportType;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DashboardReportDto implements Importer, Exporter {
  private UUID id;
  private String name;
  private String url;
  private String embeddedUuid;
  private ReportType type;
  private boolean enabled;
  private boolean showOnHomePage;
  private ReportCategory category;
  private String rightName;

  /**
   * Create new instance of DashboardReportDto based on given {@link DashboardReport}.
   *
   * @param dashboardReport instance of Dashboard Report.
   * @return new instance of DashboardReportDto.
   */
  public static DashboardReportDto newInstance(DashboardReport dashboardReport) {
    if (dashboardReport == null) {
      return null;
    }
    DashboardReportDto dashboardReportDto = new DashboardReportDto();
    dashboardReport.export(dashboardReportDto);
    return dashboardReportDto;
  }

  /**
   * Create new list of DashboardReportDto based on given list of {@link DashboardReport}.
   *
   * @param dashboardReports list of {@link DashboardReport}.
   * @return new list of DashboardReportDto.
   */
  public static List<DashboardReportDto> newInstance(Iterable<DashboardReport> dashboardReports) {
    return StreamSupport.stream(dashboardReports.spliterator(), false)
      .map(DashboardReportDto::newInstance)
      .collect(Collectors.toList());
  }
}