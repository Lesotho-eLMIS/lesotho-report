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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openlmis.report.exception.NotFoundMessageException;
import org.openlmis.report.exception.PermissionMessageException;
import org.openlmis.report.repository.DashboardReportRepository;
import org.openlmis.report.service.PermissionService;
import org.openlmis.report.service.SupersetService;
import org.openlmis.report.utils.Message;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class SupersetGuestTokenControllerTest {

  private static final String EMBEDDED_UUID = "test-dashboard-uuid";
  private static final String USERNAME = "testuser";
  private static final String GUEST_TOKEN = "mock-guest-token";

  @Mock
  private SupersetService supersetService;

  @Mock
  private DashboardReportRepository dashboardReportRepository;

  @Mock
  private PermissionService permissionService;

  @InjectMocks
  private SupersetGuestTokenController controller;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USERNAME, "password")
    );
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void shouldReturnGuestToken() {
    when(dashboardReportRepository.existsByEmbeddedUuid(EMBEDDED_UUID)).thenReturn(true);
    when(supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME))
        .thenReturn(GUEST_TOKEN);

    Map<String, String> result = controller.getGuestToken(EMBEDDED_UUID);

    assertEquals(GUEST_TOKEN, result.get("token"));
    assertEquals(1, result.size());
    verify(permissionService).canViewReports();
  }

  @Test(expected = NotFoundMessageException.class)
  public void shouldThrowNotFoundWhenDashboardDoesNotExist() {
    when(dashboardReportRepository.existsByEmbeddedUuid(EMBEDDED_UUID)).thenReturn(false);

    try {
      controller.getGuestToken(EMBEDDED_UUID);
    } finally {
      verify(supersetService, never()).getGuestToken(
          EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
    }
  }

  @Test(expected = PermissionMessageException.class)
  public void shouldThrowWhenUserLacksReportsViewRight() {
    doThrow(new PermissionMessageException(new Message("report.error.noPermission")))
        .when(permissionService).canViewReports();

    try {
      controller.getGuestToken(EMBEDDED_UUID);
    } finally {
      verify(dashboardReportRepository, never()).existsByEmbeddedUuid(EMBEDDED_UUID);
      verify(supersetService, never()).getGuestToken(
          EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
    }
  }
}
