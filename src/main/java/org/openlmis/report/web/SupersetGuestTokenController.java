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

package org.openlmis.report.web;

import java.util.Collections;
import java.util.Map;
import org.openlmis.report.exception.NotFoundMessageException;
import org.openlmis.report.i18n.SupersetMessageKeys;
import org.openlmis.report.repository.DashboardReportRepository;
import org.openlmis.report.service.PermissionService;
import org.openlmis.report.service.SupersetService;
import org.openlmis.report.utils.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/api/reports/superset")
public class SupersetGuestTokenController extends BaseController {

  @Autowired
  private SupersetService supersetService;

  @Autowired
  private DashboardReportRepository dashboardReportRepository;

  @Autowired
  private PermissionService permissionService;

  /**
   * Get a Superset guest token for embedding a dashboard.
   *
   * @param embeddedUuid the embedded UUID of the Superset dashboard
   * @return a map containing the guest token
   */
  @GetMapping("/guest-token")
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  public Map<String, String> getGuestToken(@RequestParam String embeddedUuid) {
    permissionService.canViewReports();

    if (!dashboardReportRepository.existsByEmbeddedUuid(embeddedUuid)) {
      throw new NotFoundMessageException(
          new Message(SupersetMessageKeys.ERROR_SUPERSET_DASHBOARD_NOT_FOUND, embeddedUuid));
    }

    String username = SecurityContextHolder.getContext().getAuthentication().getName();

    String token = supersetService.getGuestToken(
        embeddedUuid, username, username, username
    );

    return Collections.singletonMap("token", token);
  }
}
