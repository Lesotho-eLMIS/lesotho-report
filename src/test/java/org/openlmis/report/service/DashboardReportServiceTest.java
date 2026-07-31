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

package org.openlmis.report.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.report.dto.DashboardReportDto;
import org.openlmis.report.exception.ValidationMessageException;
import org.openlmis.report.i18n.DashboardReportMessageKeys;
import org.openlmis.report.repository.DashboardReportRepository;
import org.openlmis.report.repository.ReportCategoryRepository;
import org.openlmis.report.service.referencedata.RightReferenceDataService;
import org.openlmis.report.utils.Message;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("PMD.UnusedPrivateField")
public class DashboardReportServiceTest {

  @Mock
  private ReportCategoryRepository reportCategoryRepository;

  @Mock
  private DashboardReportRepository dashboardReportRepository;

  @Mock
  private RightReferenceDataService rightReferenceDataService;

  @Mock
  private PermissionService permissionService;

  @InjectMocks
  private DashboardReportService dashboardReportService;

  @Test
  public void shouldRejectCreateWhenBothUrlAndEmbeddedUuidAreBlank() {
    DashboardReportDto dto = new DashboardReportDto();
    dto.setName("Report");
    dto.setUrl(null);
    dto.setEmbeddedUuid("");

    try {
      dashboardReportService.createDashboardReport(dto);
      fail("Expected ValidationMessageException");
    } catch (ValidationMessageException ex) {
      assertEquals(new Message(DashboardReportMessageKeys.ERROR_URL_OR_EMBEDDED_UUID_REQUIRED),
          ex.asMessage());
    }
  }

  @Test
  public void shouldRejectUpdateWhenBothUrlAndEmbeddedUuidAreBlank() {
    DashboardReportDto dto = new DashboardReportDto();
    dto.setName("Report");
    dto.setUrl("");
    dto.setEmbeddedUuid(null);

    try {
      dashboardReportService.updateDashboardReport(UUID.randomUUID(), dto);
      fail("Expected ValidationMessageException");
    } catch (ValidationMessageException ex) {
      assertEquals(new Message(DashboardReportMessageKeys.ERROR_URL_OR_EMBEDDED_UUID_REQUIRED),
          ex.asMessage());
    }
  }
}
