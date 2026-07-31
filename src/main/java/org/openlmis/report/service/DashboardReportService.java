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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openlmis.report.domain.DashboardReport;
import org.openlmis.report.domain.ReportCategory;
import org.openlmis.report.dto.DashboardReportDto;
import org.openlmis.report.dto.external.referencedata.RightDto;
import org.openlmis.report.dto.external.referencedata.RightType;
import org.openlmis.report.exception.NotFoundMessageException;
import org.openlmis.report.exception.ValidationMessageException;
import org.openlmis.report.i18n.DashboardReportMessageKeys;
import org.openlmis.report.i18n.ReportCategoryMessageKeys;
import org.openlmis.report.repository.DashboardReportRepository;
import org.openlmis.report.repository.ReportCategoryRepository;
import org.openlmis.report.service.referencedata.RightReferenceDataService;
import org.openlmis.report.utils.Message;
import org.openlmis.report.utils.Pagination;
import org.openlmis.report.utils.PropertyKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DashboardReportService {
  private final ReportCategoryRepository reportCategoryRepository;
  private final DashboardReportRepository dashboardReportRepository;
  private final RightReferenceDataService rightReferenceDataService;
  private final PermissionService permissionService;

  /**
   * Constructs a new {@link ReportCategoryService} with the specified dependencies.
   *
   * @param reportCategoryRepository The repository responsible for handling report category data.
   * @param permissionService The service responsible for checking and managing user permissions.
   * @param dashboardReportRepository The repository for managing dashboard reports.
   */
  @Autowired
  public DashboardReportService(ReportCategoryRepository reportCategoryRepository,
      PermissionService permissionService, DashboardReportRepository dashboardReportRepository,
      RightReferenceDataService rightReferenceDataService) {
    this.reportCategoryRepository = reportCategoryRepository;
    this.dashboardReportRepository = dashboardReportRepository;
    this.rightReferenceDataService = rightReferenceDataService;
    this.permissionService = permissionService;
  }

  /**
   * Retrieves a dashboard report by its ID.
   *
   * @param reportId UUID of the dashboard report to retrieve.
   * @return DashboardReportDto containing the dashboard report details.
   */
  public DashboardReportDto getDashboardReport(UUID reportId) {
    permissionService.canManageReports();

    DashboardReport dashboardReport = dashboardReportRepository.findById(reportId)
        .orElseThrow(() -> new NotFoundMessageException(new Message(
            DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_NOT_FOUND, reportId)));

    return DashboardReportDto.newInstance(dashboardReport);
  }

  /**
   * Retrieves a page of dashboard reports, optionally filtered by the showOnHomePage parameter.
   *
   * @param pageable Pageable parameters for pagination.
   * @return A page of dashboard reports matching the criteria.
   */
  public Page<DashboardReportDto> getDashboardReports(Pageable pageable) {
    permissionService.canManageReports();
    Page<DashboardReport> dashboardReports = dashboardReportRepository.findAll(pageable);
    return Pagination.getPage(dashboardReports.map(DashboardReportDto::newInstance),pageable);
  }

  /**
   * Retrieves a page of dashboard reports for which user has rights, or retrieves
   * a home page report.
   *
   * @param pageable Pageable parameters for pagination.
   * @param showOnHomePage Filter to only include report that is shown on the home page.
   * @return A page of dashboard reports matching the criteria.
   */
  public Page<DashboardReportDto> getDashboardReportsForUser(Pageable pageable,
      boolean showOnHomePage) {
    if (!showOnHomePage) {
      permissionService.canViewReports();
    }

    List<String> accessibleRights = permissionService.filterRightsForUser(
        dashboardReportRepository.findByEnabled(true, pageable).getContent().stream()
            .map(DashboardReport::getRightName)
            .filter(Objects::nonNull)
            .collect(Collectors.toList())
    );

    if (accessibleRights.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    Page<DashboardReport> dashboardReports = dashboardReportRepository
        .findByRightsAndShowOnHomePage(
            accessibleRights,
            showOnHomePage ? Boolean.TRUE : null,
            pageable
        );

    return Pagination.getPage(dashboardReports.map(DashboardReportDto::newInstance), pageable);
  }

  /**
   * Deletes the dashboard report with the given ID.
   *
   * @param reportId UUID of the dashboard report to delete.
   * @throws NotFoundMessageException if no dashboard report with the given ID is found.
   */
  public void deleteDashboardReport(UUID reportId) {
    permissionService.canManageReports();

    DashboardReport dashboardReport = dashboardReportRepository.findById(reportId)
        .orElseThrow(() -> new NotFoundMessageException(new Message(
            DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_NOT_FOUND, reportId)));

    RightDto foundRight = rightReferenceDataService.findRight(dashboardReport.getRightName());
    rightReferenceDataService.delete(foundRight.getId());
    dashboardReportRepository.delete(dashboardReport);
  }

  /**
   * Adds a new dashboard report to the database.
   *
   * @param dto Data transfer object containing Dashboard Report data.
   * @return Saved dashboard report.
   */
  public DashboardReportDto createDashboardReport(DashboardReportDto dto) {
    permissionService.canManageReports();
    validateUrlOrEmbeddedUuid(dto);

    boolean reportExists = dashboardReportRepository.existsByName(dto.getName());
    if (reportExists) {
      throw new ValidationMessageException(new Message(
          DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_NAME_DUPLICATED, dto.getName()));
    }

    DashboardReport dashboardReportToCreate = new DashboardReport();
    updateFrom(dto, dashboardReportToCreate);
    dashboardReportToCreate.setId(null);

    RightDto rightToSave = createRight(dashboardReportToCreate.getName());
    dashboardReportToCreate.setRightName(rightToSave.getName());

    try {
      rightReferenceDataService.save(rightToSave);
    } catch (Exception ex) {
      throw new ValidationMessageException(new Message(
          DashboardReportMessageKeys.ERROR_COULD_NOT_SAVE_RIGHT), ex);
    }

    try {
      return DashboardReportDto.newInstance(saveDashboardReport(dashboardReportToCreate));
    } catch (DataIntegrityViolationException ex) {
      throw new ValidationMessageException(new Message(
          DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_NAME_DUPLICATED, dto.getName()), ex);
    }
  }

  /**
   * Updates a dashboard report in the database.
   *
   * @param id The UUID of the dashboard report to update.
   * @param dashboardReportDto The DTO containing the updated dashboard report data.
   * @return The updated DashboardReportDto.
   */
  public DashboardReportDto updateDashboardReport(UUID id, DashboardReportDto dashboardReportDto) {
    permissionService.canManageReports();
    validateUrlOrEmbeddedUuid(dashboardReportDto);

    if (dashboardReportDto.getId() != null && !Objects.equals(dashboardReportDto.getId(), id)) {
      throw new ValidationMessageException(new Message(
        DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_ID_MISMATCH));
    }

    DashboardReport dashboardReport = dashboardReportRepository.findById(id)
        .orElseThrow(() -> new NotFoundMessageException(new Message(
            DashboardReportMessageKeys.ERROR_DASHBOARD_REPORT_NOT_FOUND, id)));

    updateFrom(dashboardReportDto, dashboardReport);
    saveDashboardReport(dashboardReport);

    return DashboardReportDto.newInstance(dashboardReport);
  }

  /**
   * Update a dashboard report with the data from DTO.
   *
   * @param newReport The DashboardReportDto containing the category ID.
   * @param reportToUpdate Report which is being updated.
   */
  private void updateFrom(DashboardReportDto newReport, DashboardReport reportToUpdate) {
    reportToUpdate.updateFrom(newReport);
    reportToUpdate.setCategory(getCategoryOrThrow(newReport));
  }

  // A report needs either a URL or an Embedded UUID (Superset embedded dashboards use the latter).
  private void validateUrlOrEmbeddedUuid(DashboardReportDto dto) {
    if (StringUtils.isBlank(dto.getUrl()) && StringUtils.isBlank(dto.getEmbeddedUuid())) {
      throw new ValidationMessageException(new Message(
          DashboardReportMessageKeys.ERROR_URL_OR_EMBEDDED_UUID_REQUIRED));
    }
  }

  /**
   * Retrieves the ReportCategory from the database using the category ID
   * from the provided DTO. If the category doesn't exist, throws a
   * NotFoundMessageException.
   *
   * @param newReport The DashboardReportDto containing the category ID
   * @return The corresponding ReportCategory
   * @throws NotFoundMessageException if the category doesn't exist
   */
  private ReportCategory getCategoryOrThrow(DashboardReportDto newReport) {
    if (newReport.getCategory() == null) {
      throw new NotFoundMessageException(new Message(
          ReportCategoryMessageKeys.ERROR_REPORT_CATEGORY_NOT_FOUND));
    }

    return reportCategoryRepository.findById(newReport.getCategory().getId())
      .orElseThrow(() -> new NotFoundMessageException(new Message(
          ReportCategoryMessageKeys.ERROR_REPORT_CATEGORY_NOT_FOUND)));
  }

  /**
   * Creates right for new dashboard report.
   *
   * @param dashboardReportName Name of the dashboard report used to create right name
   * @return New RightDto
   */
  private RightDto createRight(String dashboardReportName) {
    String transformedName = PropertyKeyUtil.transformToPropertyKey(dashboardReportName);

    if (null == transformedName) {
      throw new ValidationMessageException(new Message(
        DashboardReportMessageKeys.ERROR_COULD_NOT_SAVE_RIGHT));
    }

    RightDto rightToSave = new RightDto();
    rightToSave.setName(transformedName);
    rightToSave.setType(RightType.REPORTS);
    return rightToSave;
  }

  /**
   * Saves dashboard report and validates if any
   * report has showOnHomepage set to true and overrides it.
   */
  public DashboardReport saveDashboardReport(DashboardReport dashboardReport) {
    DashboardReport dashboardReportToReturn = dashboardReportRepository.save(dashboardReport);

    if (dashboardReport.isShowOnHomePage()) {
      List<DashboardReport> existingReports = dashboardReportRepository.findByShowOnHomePage(true);

      existingReports.stream()
          .filter(report -> !report.getId().equals(dashboardReportToReturn.getId()))
          .forEach(report -> {
            report.setShowOnHomePage(false);
            dashboardReportRepository.save(report);
          });
    }

    return dashboardReportToReturn;
  }
}
