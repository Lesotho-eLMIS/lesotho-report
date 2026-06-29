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

package org.openlmis.report.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Getter
@Setter
@Builder
@Entity
@Table(name = "jasper_templates")
@NoArgsConstructor
@AllArgsConstructor
public class JasperTemplate extends BaseEntity {

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION, unique = true, nullable = false)
  private String name;

  @Column
  private byte[] data;

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION)
  private String type;

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION)
  private String description;

  @ElementCollection
  @CollectionTable
  private List<String> requiredRights;

  @OneToMany(
      mappedBy = "template",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.REMOVE},
      fetch = FetchType.EAGER,
      orphanRemoval = true)
  @Fetch(FetchMode.SELECT)
  @OrderBy("displayOrder ASC NULLS LAST")
  private List<JasperTemplateParameter> templateParameters;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "jasper_templates_report_images",
      joinColumns = @JoinColumn(name = "jaspertemplateid", nullable = false),
      inverseJoinColumns = @JoinColumn(name = "reportimageid", nullable = false))
  private Set<ReportImage> reportImages = new HashSet<>();

  private Boolean visible;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "categoryid", referencedColumnName = "id", nullable = false)
  private ReportCategory category;

  /**
   * Export this object to the specified exporter (DTO).
   *
   * @param exporter exporter to export to
   */
  public void export(Exporter exporter) {
    exporter.setData(data);
    exporter.setDescription(description);
    exporter.setId(id);
    exporter.setName(name);
    exporter.setType(type);
    exporter.setCategory(category);
  }

  @PrePersist
  @PreUpdate
  private void preSave() {
    if (templateParameters != null) {
      templateParameters.forEach(line -> line.setTemplate(this));
    }

    if (null == visible) {
      visible = true;
    }
  }

  public interface Exporter {
    void setId(UUID id);

    void setName(String name);

    void setData(byte[] data);

    void setType(String type);

    void setDescription(String description);

    void setRequiredRights(List<String> rights);

    void setCategory(ReportCategory category);
  }
}
