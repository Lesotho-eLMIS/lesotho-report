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

package org.openlmis.report.repository;

import java.util.List;
import java.util.UUID;
import org.openlmis.report.domain.DashboardReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

public interface DashboardReportRepository
    extends PagingAndSortingRepository<DashboardReport, UUID> {

  Page<DashboardReport> findByEnabled(boolean enabled, Pageable pageable);

  boolean existsByName(String name);

  boolean existsByEmbeddedUuid(String embeddedUuid);

  List<DashboardReport> findByShowOnHomePage(boolean showOnHomePage);

  boolean existsByCategory_Id(UUID categoryId);

  /**
   * Retrieves a page of dashboard reports for which user has rights. If showOnHomePage
   * was provided and user has rights, the home page report will be returned.
   *
   * @param pageable Pageable parameters for pagination.
   * @param rightNames Names of user rights.
   * @param showOnHomePage Filter to only include report that is shown on the home page.
   * @return A page of dashboard reports matching the criteria.
   */
  @Query("SELECT r FROM DashboardReport r WHERE r.enabled = true "
      + "AND r.rightName IN :rightNames "
      + "AND (:showOnHomePage IS NULL OR r.showOnHomePage = :showOnHomePage)")
  Page<DashboardReport> findByRightsAndShowOnHomePage(
      @Param("rightNames") List<String> rightNames,
      @Param("showOnHomePage") Boolean showOnHomePage,
      Pageable pageable);
}
