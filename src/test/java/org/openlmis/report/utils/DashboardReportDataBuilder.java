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

package org.openlmis.report.utils;

import java.util.UUID;
import org.apache.commons.lang.RandomStringUtils;
import org.openlmis.report.domain.DashboardReport;
import org.openlmis.report.domain.ReportCategory;

public class DashboardReportDataBuilder {
  private final UUID id = UUID.randomUUID();
  private String name = RandomStringUtils.random(6);
  private String url = "http://example.com";
  private String embeddedUuid = null;
  private ReportType type = ReportType.SUPERSET;
  private boolean enabled = true;
  private boolean showOnHomePage = false;
  private ReportCategory reportCategory = new ReportCategoryDataBuilder().buildAsNew();
  private final String rightName = (this.name + "_RIGHT").toUpperCase();

  public DashboardReportDataBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public DashboardReportDataBuilder withUrl(String url) {
    this.url = url;
    return this;
  }

  public DashboardReportDataBuilder withType(ReportType type) {
    this.type = type;
    return this;
  }

  public DashboardReportDataBuilder withEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public DashboardReportDataBuilder withShowOnHomePage(boolean showOnHomePage) {
    this.showOnHomePage = showOnHomePage;
    return this;
  }

  public DashboardReportDataBuilder withCategory(ReportCategory reportCategory) {
    this.reportCategory = reportCategory;
    return this;
  }

  /**
   * Builds an instance of the {@link DashboardReport} class with populated ID.
   *
   * @return the instance of {@link DashboardReport} class
   */
  public DashboardReport build() {
    DashboardReport dashboardReport = buildAsNew();
    dashboardReport.setId(id);
    return dashboardReport;
  }

  /**
   * Build an instance of the {@link DashboardReport} class without ID field populated.
   *
   * @return the instance of {@link DashboardReport} class
   */
  public DashboardReport buildAsNew() {
    return new DashboardReport(
      name,
      url,
      embeddedUuid,
      type,
      enabled,
      showOnHomePage,
      reportCategory,
      rightName
    );
  }
}